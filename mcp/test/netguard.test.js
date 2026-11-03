import { test } from "node:test";
import assert from "node:assert/strict";
import { assertPublicHost, isBlockedAddress, AddressBlockedError } from "../src/netguard.js";

test("isBlockedAddress rejects loopback, private, link-local, CGNAT (IPv4)", () => {
  for (const ip of ["127.0.0.1", "10.0.0.5", "172.16.4.4", "192.168.1.1", "169.254.1.1", "100.64.0.1"]) {
    assert.equal(isBlockedAddress(ip), true, `${ip} should be blocked`);
  }
});

test("isBlockedAddress rejects IPv6 loopback, ULA, and link-local", () => {
  for (const ip of ["::1", "fd00::1", "fc00::1", "fe80::1"]) {
    assert.equal(isBlockedAddress(ip), true, `${ip} should be blocked`);
  }
});

test("isBlockedAddress allows ordinary public addresses", () => {
  for (const ip of ["93.184.216.34", "8.8.8.8", "2606:4700:4700::1111"]) {
    assert.equal(isBlockedAddress(ip), false, `${ip} should be allowed`);
  }
});

test("assertPublicHost rejects a hostname resolving to a private address", async () => {
  const lookup = async () => [{ address: "10.1.2.3", family: 4 }];
  await assert.rejects(
    () => assertPublicHost("internal.example", { lookup }),
    (err) => err instanceof AddressBlockedError,
  );
});

test("assertPublicHost resolves fine for a hostname resolving to a public address", async () => {
  const lookup = async () => [{ address: "93.184.216.34", family: 4 }];
  await assert.doesNotReject(() => assertPublicHost("example.com", { lookup }));
});

test("assertPublicHost checks a literal loopback IP directly, without calling lookup", async () => {
  await assert.rejects(
    () => assertPublicHost("127.0.0.1", { lookup: () => Promise.reject(new Error("should not be called")) }),
    (err) => err instanceof AddressBlockedError,
  );
});

test("isBlockedAddress judges IPv4 written as IPv6 by the IPv4 inside", () => {
  for (const ip of [
    "::ffff:127.0.0.1", "::ffff:7f00:1", "0:0:0:0:0:ffff:7f00:1", "::ffff:a9fe:a9fe",
    "::127.0.0.1", "::", "64:ff9b::7f00:1", "64:ff9b::10.0.0.1", "2002:7f00:1::1", "2002:c0a8:101::",
  ]) {
    assert.equal(isBlockedAddress(ip), true, `${ip} should be blocked`);
  }
  for (const ip of ["::ffff:8.8.8.8", "::ffff:808:808", "64:ff9b::808:808", "2002:808:808::1"]) {
    assert.equal(isBlockedAddress(ip), false, `${ip} should be allowed`);
  }
});

test("isBlockedAddress rejects benchmarking, multicast and broadcast", () => {
  for (const ip of ["198.18.0.1", "198.19.255.255", "224.0.0.1", "239.1.1.1", "255.255.255.255", "ff02::1", "fe80::1%en0"]) {
    assert.equal(isBlockedAddress(ip), true, `${ip} should be blocked`);
  }
});

test("assertPublicHost unwraps a bracketed IPv6 literal from new URL()", async () => {
  const { hostname } = new URL("http://[::ffff:127.0.0.1]/");
  await assert.rejects(
    () => assertPublicHost(hostname, { lookup: () => Promise.reject(new Error("should not be called")) }),
    (err) => err instanceof AddressBlockedError,
  );
});

test("assertPublicHost rejects a hostname whose AAAA record hides loopback", async () => {
  const lookup = async () => [{ address: "93.184.216.34", family: 4 }, { address: "::ffff:7f00:1", family: 6 }];
  await assert.rejects(() => assertPublicHost("rebind.example", { lookup }), (err) => err instanceof AddressBlockedError);
});
