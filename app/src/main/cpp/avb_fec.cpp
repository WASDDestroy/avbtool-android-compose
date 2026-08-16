// SPDX-License-Identifier: Apache-2.0
//
// AVBTool Android native FEC bridge.
//
// This file is part of the AVBTool Android project and is licensed
// under the Apache License, Version 2.0. It dynamically links against
// libfec_rs.so, which contains LGPL-2.1-licensed Reed-Solomon code
// (Copyright 2002-2004 Phil Karn, KA9Q). See fec_rs/ for source.

#include <cstdint>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>

// libfec_rs (AOSP external/fec, LGPL-2.1) char-based Reed-Solomon codec.
extern "C" {
void *init_rs_char(int symsize, int gfpoly, int fcr, int prim,
                   int nroots, int pad);
void encode_rs_char(void *p, unsigned char *data, unsigned char *parity);
void free_rs_char(void *p);
}

namespace {

constexpr uint32_t FEC_MAGIC = 0xFECFECFE;
constexpr uint32_t FEC_VERSION = 0;
constexpr uint32_t FEC_BLOCKSIZE = 4096;
constexpr uint32_t FEC_RSM = 255;

// Minimal public-domain SHA-256 implementation for the FEC header hash.
struct Sha256 {
  static constexpr uint32_t K[64] = {
    0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
    0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
    0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
    0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
    0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
    0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
    0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
    0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2};

  uint32_t h[8] = {0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,
                   0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19};
  uint64_t total = 0;
  uint8_t buf[64];
  size_t buf_len = 0;

  static uint32_t rotr(uint32_t x, int n) { return (x >> n) | (x << (32 - n)); }

  void block(const uint8_t *p) {
    uint32_t w[64];
    for (int i = 0; i < 16; ++i) {
      w[i] = (uint32_t(p[i * 4]) << 24) | (uint32_t(p[i * 4 + 1]) << 16) |
             (uint32_t(p[i * 4 + 2]) << 8) | uint32_t(p[i * 4 + 3]);
    }
    for (int i = 16; i < 64; ++i) {
      uint32_t s0 = rotr(w[i - 15], 7) ^ rotr(w[i - 15], 18) ^ (w[i - 15] >> 3);
      uint32_t s1 = rotr(w[i - 2], 17) ^ rotr(w[i - 2], 19) ^ (w[i - 2] >> 10);
      w[i] = w[i - 16] + s0 + w[i - 7] + s1;
    }
    uint32_t a = h[0], b = h[1], c = h[2], d = h[3];
    uint32_t e = h[4], f = h[5], g = h[6], hh = h[7];
    for (int i = 0; i < 64; ++i) {
      uint32_t S1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25);
      uint32_t ch = (e & f) ^ ((~e) & g);
      uint32_t t1 = hh + S1 + ch + K[i] + w[i];
      uint32_t S0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22);
      uint32_t maj = (a & b) ^ (a & c) ^ (b & c);
      uint32_t t2 = S0 + maj;
      hh = g; g = f; f = e; e = d + t1;
      d = c; c = b; b = a; a = t1 + t2;
    }
    h[0] += a; h[1] += b; h[2] += c; h[3] += d;
    h[4] += e; h[5] += f; h[6] += g; h[7] += hh;
  }

  void update(const uint8_t *data, size_t len) {
    total += len;
    while (len > 0) {
      size_t n = sizeof(buf) - buf_len;
      if (n > len) n = len;
      memcpy(buf + buf_len, data, n);
      buf_len += n;
      data += n;
      len -= n;
      if (buf_len == sizeof(buf)) {
        block(buf);
        buf_len = 0;
      }
    }
  }

  void finish(uint8_t out[32]) {
    uint64_t bits = total * 8;
    uint8_t pad = 0x80;
    update(&pad, 1);
    uint8_t zero = 0;
    while (buf_len != 56) update(&zero, 1);
    uint8_t lenb[8];
    for (int i = 0; i < 8; ++i) lenb[i] = uint8_t(bits >> (56 - i * 8));
    update(lenb, 8);
    for (int i = 0; i < 8; ++i) {
      out[i * 4] = uint8_t(h[i] >> 24);
      out[i * 4 + 1] = uint8_t(h[i] >> 16);
      out[i * 4 + 2] = uint8_t(h[i] >> 8);
      out[i * 4 + 3] = uint8_t(h[i]);
    }
  }
};

uint64_t div_round_up(uint64_t x, uint64_t y) {
  return (x / y) + (x % y > 0 ? 1 : 0);
}

uint64_t fec_ecc_interleave(uint64_t offset, uint64_t rsn, uint64_t rounds) {
  return (offset / rsn) + (offset % rsn) * rounds * FEC_BLOCKSIZE;
}

int write_all(int fd, const uint8_t *buf, size_t len) {
  size_t off = 0;
  while (off < len) {
    ssize_t n = ::write(fd, buf + off, len - off);
    if (n <= 0) return -1;
    off += size_t(n);
  }
  return 0;
}

int read_all(int fd, uint8_t *buf, size_t len) {
  size_t off = 0;
  while (off < len) {
    ssize_t n = ::pread(fd, buf + off, len - off, (off_t)off);
    if (n <= 0) return -1;
    off += size_t(n);
  }
  return 0;
}

int64_t file_size(int fd) {
  struct stat st;
  if (fstat(fd, &st) != 0) return -1;
  return (int64_t)st.st_size;
}

}  // namespace

extern "C" uint64_t avb_fec_print_size(uint64_t image_size, int roots) {
  if (roots <= 0 || roots >= (int)FEC_RSM || image_size == 0) return 0;
  uint64_t blocks = div_round_up(image_size, FEC_BLOCKSIZE);
  uint64_t rounds = div_round_up(blocks, FEC_RSM - (uint64_t)roots);
  return rounds * (uint64_t)roots * FEC_BLOCKSIZE + FEC_BLOCKSIZE;
}

extern "C" int avb_fec_encode(int in_fd, const char *out_path, int roots) {
  if (roots <= 0 || roots >= (int)FEC_RSM) return -1;
  if (in_fd < 0 || out_path == nullptr) return -1;

  int64_t size64 = file_size(in_fd);
  if (size64 < 0) return -1;
  uint64_t inp_size = (uint64_t)size64;
  if (inp_size == 0 || (inp_size % FEC_BLOCKSIZE) != 0) return -1;

  uint64_t blocks = div_round_up(inp_size, FEC_BLOCKSIZE);
  uint64_t rsn = FEC_RSM - (uint64_t)roots;
  uint64_t rounds = div_round_up(blocks, rsn);
  uint64_t fec_size = rounds * (uint64_t)roots * FEC_BLOCKSIZE;

  void *rs = init_rs_char(8, 0x11d, 0, 1, roots, 0);
  if (rs == nullptr) return -1;

  int out_fd = ::open(out_path, O_WRONLY | O_CREAT | O_TRUNC, 0666);
  if (out_fd < 0) {
    free_rs_char(rs);
    return -1;
  }

  std::vector<uint8_t> fec(fec_size);
  std::vector<uint8_t> parity(roots);

  const uint64_t codewords = rounds * FEC_BLOCKSIZE;
  const uint64_t stride = codewords;  // fec_ecc_interleave row stride
  const uint64_t BLOCK = 65536;       // codewords per block
  std::vector<uint8_t> data(BLOCK * rsn);
  std::vector<uint8_t> colbuf(BLOCK);

  auto pread_exact = [&](int fd, uint8_t *dst, size_t len, uint64_t off) -> int {
    size_t got = 0;
    while (got < len) {
      ssize_t n = ::pread(fd, dst + got, len - got, (off_t)(off + got));
      if (n < 0) return -1;
      if (n == 0) break;  // EOF: remainder is zero-padded
      got += (size_t)n;
    }
    memset(dst + got, 0, len - got);
    return 0;
  };

  int rc = 0;
  for (uint64_t cw_start = 0; cw_start < codewords && rc == 0; cw_start += BLOCK) {
    uint64_t block_len = codewords - cw_start;
    if (block_len > BLOCK) block_len = BLOCK;

    for (uint64_t j = 0; j < rsn; ++j) {
      uint64_t off = j * stride + cw_start;
      if (pread_exact(in_fd, colbuf.data(), (size_t)block_len, off) != 0) {
        rc = -1;
        break;
      }
      for (uint64_t c = 0; c < block_len; ++c) {
        data[c * rsn + j] = colbuf[c];
      }
    }
    if (rc != 0) break;

    for (uint64_t c = 0; c < block_len; ++c) {
      encode_rs_char(rs, data.data() + c * rsn, parity.data());
      memcpy(fec.data() + (cw_start + c) * (uint64_t)roots, parity.data(), roots);
    }
  }

  if (rc == 0) {
    if (write_all(out_fd, fec.data(), fec.size()) != 0) rc = -1;
  }

  if (rc == 0) {
    uint8_t header[FEC_BLOCKSIZE];
    memset(header, 0, sizeof(header));
    // fec_header is packed at the start of the header block, and a copy is
    // placed in the final 60 bytes of the block (AOSP image_ecc_save).
    auto write_header = [&](uint8_t *hdr) {
      uint32_t magic = FEC_MAGIC;
      uint32_t version = FEC_VERSION;
      uint32_t header_size = 60;  // sizeof(fec_header)
      uint32_t roots32 = (uint32_t)roots;
      uint32_t fec_size32 = (uint32_t)fec_size;
      uint64_t inp_size64 = inp_size;
      uint8_t hash[32];
      Sha256 sha;
      sha.update(fec.data(), fec.size());
      sha.finish(hash);

      memcpy(hdr, &magic, 4);
      memcpy(hdr + 4, &version, 4);
      memcpy(hdr + 8, &header_size, 4);
      memcpy(hdr + 12, &roots32, 4);
      memcpy(hdr + 16, &fec_size32, 4);
      memcpy(hdr + 20, &inp_size64, 8);
      memcpy(hdr + 28, hash, 32);
    };
    write_header(header);
    memcpy(header + FEC_BLOCKSIZE - 60, header, 60);
    if (write_all(out_fd, header, sizeof(header)) != 0) rc = -1;
  }

  ::close(out_fd);
  free_rs_char(rs);
  return rc;
}
