# SPDX-License-Identifier: Apache-2.0
"""Android-specific helpers for the bundled avbtool.py.

This module replaces avbtool's two external subprocess dependencies:

* ``openssl`` is replaced with the ``cryptography`` package (native code,
  installed by Chaquopy).
* ``fec`` is replaced with a small in-process native library
  (``libavbfec.so``) which implements AOSP's RS(255, N) FEC encoder.

It also installs a ``builtins.open`` hook which turns pseudo-paths of the
form ``/saf/fd/<fd>`` into ``os.fdopen`` calls.  This lets Kotlin pass
SAF file descriptors directly into Python without copying multi-GiB
images.
"""

import builtins
import ctypes
import os


_libfec = None
_libfec_rs = None


def init(native_lib_dir):
    """Must be called once after Python starts and before running avbtool."""
    global _libfec, _libfec_rs
    if _libfec is None:
        # libavbfec.so has a DT_NEEDED dependency on libfec_rs.so.
        # Preload libfec_rs.so by absolute path so the dynamic linker can
        # resolve that dependency regardless of the Android linker search
        # path. Keep the handle alive for the lifetime of the process.
        if _libfec_rs is None:
            _libfec_rs = ctypes.CDLL(os.path.join(native_lib_dir, "libfec_rs.so"))
        path = os.path.join(native_lib_dir, "libavbfec.so")
        _libfec = ctypes.CDLL(path)
        _libfec.avb_fec_print_size.argtypes = [ctypes.c_uint64, ctypes.c_int]
        _libfec.avb_fec_print_size.restype = ctypes.c_uint64
        _libfec.avb_fec_encode.argtypes = [ctypes.c_int, ctypes.c_char_p, ctypes.c_int]
        _libfec.avb_fec_encode.restype = ctypes.c_int
    install_fd_open_hook()


class _SafFileWrapper(object):
    """File object wrapper that preserves the /saf/fd path as .name.

    os.fdopen() sets .name to the numeric file descriptor. avbtool uses
    file.name as a filesystem path in several places, which breaks when
    it receives an int. This wrapper keeps the pseudo-path while delegating
    all file operations to the real fdopen object.
    """

    def __init__(self, path, fileobj):
        self._fileobj = fileobj
        self.name = path
        self.mode = getattr(fileobj, "mode", None)
        self.closed = getattr(fileobj, "closed", False)

    def __getattr__(self, item):
        return getattr(self._fileobj, item)

    def __enter__(self):
        self._fileobj.__enter__()
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        return self._fileobj.__exit__(exc_type, exc_value, traceback)

    def __iter__(self):
        return iter(self._fileobj)

    def __next__(self):
        return next(self._fileobj)


def install_fd_open_hook():
    """Route ``/saf/fd/<n>`` pseudo-paths to ``os.fdopen``."""
    if getattr(builtins, "_avb_fd_hook_installed", False):
        return
    original_open = builtins.open

    def _open(file, mode="r", buffering=-1, encoding=None, errors=None,
              newline=None, closefd=True, opener=None):
        if isinstance(file, str) and file.startswith("/saf/fd/"):
            rest = file.rsplit("/", 1)[1]
            if not rest.isdigit():
                return original_open(file, mode, buffering=buffering,
                                     encoding=encoding, errors=errors,
                                     newline=newline, closefd=closefd,
                                     opener=opener)
            fd = int(rest)
            dup = os.dup(fd)
            # dup() shares the file offset with the original fd. avbtool opens
            # the same /saf/fd path more than once (e.g. parse key, then sign
            # with key), so rewind each duplicate to the start.
            if "a" not in mode:
                try:
                    os.lseek(dup, 0, os.SEEK_SET)
                except Exception:
                    pass
            if "b" in mode:
                fobj = os.fdopen(dup, mode, buffering=buffering)
            else:
                fobj = os.fdopen(dup, mode, buffering=buffering,
                                 encoding=encoding or "utf-8",
                                 errors=errors or "strict", newline=newline)
            return _SafFileWrapper(file, fobj)
        return original_open(file, mode, buffering=buffering, encoding=encoding,
                             errors=errors, newline=newline, closefd=closefd,
                             opener=opener)

    builtins.open = _open
    builtins._avb_fd_hook_installed = True


def fec_print_size(image_size, roots):
    """Return the same value as AOSP ``fec --print-fec-size``."""
    if _libfec is None:
        raise RuntimeError("android_bridge.init() has not been called")
    size = _libfec.avb_fec_print_size(image_size, roots)
    if size == 0:
        raise ValueError("invalid parameters for native fec")
    return int(size)


def fec_encode_fd(in_fd, out_path, roots):
    """Encode FEC data from an open fd to out_path."""
    if _libfec is None:
        raise RuntimeError("android_bridge.init() has not been called")
    return int(_libfec.avb_fec_encode(int(in_fd), out_path.encode("utf-8"), int(roots)))


def load_rsa_key(key_path):
    """Return (modulus, num_bits, exponent) for an RSA private or public key."""
    from cryptography.hazmat.primitives import serialization

    with open(key_path, "rb") as f:
        data = f.read()
    try:
        key = serialization.load_pem_private_key(data, password=None)
        numbers = key.public_key().public_numbers()
    except Exception:
        key = serialization.load_pem_public_key(data)
        numbers = key.public_numbers()
    modulus = numbers.n
    num_bits = 2 ** ((modulus.bit_length() - 1).bit_length())
    return modulus, num_bits, numbers.e


def rsa_sign(key_path, algorithm_name, data_to_sign):
    """Sign exactly like avbtool's old ``openssl rsautl -sign -raw`` path."""
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import padding

    with open(key_path, "rb") as f:
        key = serialization.load_pem_private_key(f.read(), password=None)
    hash_name = algorithm_name.split("_")[0].lower()
    chosen_hash = {"sha256": hashes.SHA256(), "sha512": hashes.SHA512()}[hash_name]
    return key.sign(data_to_sign, padding.PKCS1v15(), chosen_hash)


def rsa_verify(num_bits, modulus, sig_blob, expected):
    """Raw RSA verify with exponent 65537.

    Equivalent to avbtool's old ``openssl rsautl -verify -pubin -raw``.
    """
    if num_bits <= 0 or modulus <= 0:
        return False
    signature = int.from_bytes(sig_blob, "big")
    expected_int = int.from_bytes(expected, "big")
    return pow(signature, 65537, modulus) == expected_int


def fec_self_test():
    """Hidden debug command: encode a deterministic 1 MiB image with native FEC.

    The host reference can be generated with:
        python -c "open('selftest.img','wb').write((bytes(range(256))*16)*256)"
        fec --encode --roots 2 selftest.img ref.fec
    Then compare the reported SHA-256 values.
    """
    import hashlib
    import os
    import struct
    import tempfile

    if _libfec is None:
        raise RuntimeError("android_bridge.init() has not been called")

    NL = chr(10)
    roots = 2
    block = bytes(range(256)) * 16   # 4096 bytes
    data = block * 256                # 1 MiB
    img_path = None
    fec_path = None
    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=".img") as img_file:
            img_path = img_file.name
            img_file.write(data)
        fec_path = img_path + ".fec"
        with open(img_path, "rb") as img_file:
            rc = fec_encode_fd(img_file.fileno(), fec_path, roots)
        if rc != 0:
            return "native fec encoder returned " + str(rc) + NL, ""
        with open(fec_path, "rb") as fec_file:
            fec_data = fec_file.read()

        footer_format = "<LLLLLQ32s"
        footer = fec_data[-struct.calcsize(footer_format):]
        magic, version, hdr_size, roots_out, fec_size, inp_size, _ = struct.unpack(
            footer_format, footer
        )
        parity = fec_data[:fec_size]
        lines = [
            "avbtool_fec_self_test",
            "input_bytes=" + str(inp_size),
            "input_sha256=" + hashlib.sha256(data).hexdigest(),
            "roots=" + str(roots_out),
            "fec_file_bytes=" + str(len(fec_data)),
            "fec_file_sha256=" + hashlib.sha256(fec_data).hexdigest(),
            "fec_parity_bytes=" + str(fec_size),
            "fec_parity_sha256=" + hashlib.sha256(parity).hexdigest(),
            "header_magic=" + hex(magic),
            "header_version=" + str(version),
            "header_size=" + str(hdr_size),
            "header_inp_size=" + str(inp_size),
        ]
        return NL.join(lines) + NL, ""
    finally:
        if img_path:
            try:
                os.unlink(img_path)
            except OSError:
                pass
        if fec_path:
            try:
                os.unlink(fec_path)
            except OSError:
                pass

def run_avbtool(argv):
    """Run avbtool with the given argv list; return (stdout, stderr)."""
    import io
    import sys
    import traceback

    argv = list(argv)
    if argv and "avbtool_fec_self_test" in argv[:3]:
        return fec_self_test()

    import avbtool

    old_stdout = sys.stdout
    old_stderr = sys.stderr
    out = io.StringIO()
    err = io.StringIO()
    sys.stdout = out
    sys.stderr = err
    try:
        tool = avbtool.AvbTool()
        tool.run(argv)
    except SystemExit:
        pass
    except Exception:
        err.write(traceback.format_exc())
    finally:
        sys.stdout = old_stdout
        sys.stderr = old_stderr
    return out.getvalue(), err.getvalue()
