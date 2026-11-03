// Refuses to fetch loopback, private, link-local, CGNAT, benchmarking,
// multicast, and IPv6 ULA / link-local / multicast addresses, including IPv4
// addresses written as IPv6 (::ffff:7f00:1, NAT64, 6to4). Used by fetch_page
// before making any request, and re-checked on every redirect hop. Resolution is injectable so tests can
// exercise real DNS-backed hosts (or fake ones) without touching the network
// stack's real resolver.
import dns from "node:dns";
import net from "node:net";

export class AddressBlockedError extends Error {
  constructor(host, address) {
    super(`refusing to fetch ${host} (resolves to ${address}, a non-public address)`);
    this.name = "AddressBlockedError";
    this.host = host;
    this.address = address;
  }
}

function isBlockedIPv4(ip) {
  const parts = ip.split(".").map(Number);
  if (parts.length !== 4 || parts.some((p) => Number.isNaN(p) || p < 0 || p > 255)) return true;
  const [a, b] = parts;
  if (a === 0) return true; // "this network"
  if (a === 127) return true; // loopback
  if (a === 10) return true; // private
  if (a === 172 && b >= 16 && b <= 31) return true; // private
  if (a === 192 && b === 168) return true; // private
  if (a === 169 && b === 254) return true; // link-local
  if (a === 100 && b >= 64 && b <= 127) return true; // CGNAT 100.64.0.0/10
  if (a === 198 && (b === 18 || b === 19)) return true; // benchmarking 198.18.0.0/15
  if (a >= 224) return true; // multicast, reserved, broadcast
  return false;
}

/** An IPv6 address as its eight 16-bit groups, or null if it isn't one. */
function ipv6Groups(ip) {
  let s = ip.toLowerCase().split("%")[0]; // drop a zone id (fe80::1%en0)
  // A trailing dotted IPv4 (::ffff:127.0.0.1) becomes two hex groups.
  const v4 = s.match(/^(.*:)(\d+\.\d+\.\d+\.\d+)$/);
  if (v4) {
    const o = v4[2].split(".").map(Number);
    if (o.some((n) => n > 255)) return null;
    s = `${v4[1]}${((o[0] << 8) | o[1]).toString(16)}:${((o[2] << 8) | o[3]).toString(16)}`;
  }
  const halves = s.split("::");
  if (halves.length > 2) return null;
  const head = halves[0] ? halves[0].split(":") : [];
  const tail = halves.length === 2 && halves[1] ? halves[1].split(":") : [];
  const fill = halves.length === 2 ? 8 - head.length - tail.length : 0;
  if (fill < 0 || (halves.length === 1 && head.length !== 8)) return null;
  const groups = [...head, ...Array(fill).fill("0"), ...tail].map((g) => parseInt(g, 16));
  return groups.length === 8 && groups.every((g) => g >= 0 && g <= 0xffff) ? groups : null;
}

const v4From = (hi, lo) => `${hi >> 8}.${hi & 0xff}.${lo >> 8}.${lo & 0xff}`;

function isBlockedIPv6(ip) {
  const g = ipv6Groups(ip);
  if (!g) return true; // unparseable: refuse
  const zeros = (n) => g.slice(0, n).every((x) => x === 0);
  // Forms that carry an IPv4 address are judged by that address:
  // IPv4-mapped ::ffff:a.b.c.d, IPv4-compatible ::a.b.c.d (also covers :: and
  // ::1), NAT64 64:ff9b::a.b.c.d, and 6to4 2002:aabb:ccdd::.
  if (zeros(5) && g[5] === 0xffff) return isBlockedIPv4(v4From(g[6], g[7]));
  if (zeros(6)) return true; // ::, ::1 and the deprecated IPv4-compatible range
  if (g[0] === 0x64 && g[1] === 0xff9b && g.slice(2, 6).every((x) => x === 0)) return isBlockedIPv4(v4From(g[6], g[7]));
  if (g[0] === 0x2002) return isBlockedIPv4(v4From(g[1], g[2]));
  if ((g[0] & 0xfe00) === 0xfc00) return true; // ULA fc00::/7
  if ((g[0] & 0xffc0) === 0xfe80) return true; // link-local fe80::/10
  if ((g[0] & 0xff00) === 0xff00) return true; // multicast ff00::/8
  return false;
}

/** True if `ip` (v4 or v6, as returned by dns.lookup) is not a public address. */
export function isBlockedAddress(ip) {
  if (net.isIPv4(ip)) return isBlockedIPv4(ip);
  if (net.isIPv6(ip)) return isBlockedIPv6(ip);
  return true; // unrecognized shape: refuse
}

/**
 * Resolve `hostname` and throw AddressBlockedError if it (or any of its
 * resolved addresses) is not public. `lookup` is injectable for tests;
 * defaults to Node's real DNS resolution with `{ all: true }`.
 */
export async function assertPublicHost(hostname, { lookup } = {}) {
  // new URL() keeps IPv6 literals bracketed: http://[::1]/ has hostname "[::1]".
  hostname = String(hostname).replace(/^\[(.*)\]$/, "$1");
  if (net.isIP(hostname)) {
    if (isBlockedAddress(hostname)) throw new AddressBlockedError(hostname, hostname);
    return;
  }
  const doLookup =
    lookup ||
    ((host, opts) => new Promise((resolve, reject) => dns.lookup(host, opts, (err, addrs) => (err ? reject(err) : resolve(addrs)))));

  const addrs = await doLookup(hostname, { all: true });
  if (!addrs || addrs.length === 0) throw new Error(`could not resolve host ${hostname}`);
  for (const entry of addrs) {
    const address = typeof entry === "string" ? entry : entry.address;
    if (isBlockedAddress(address)) throw new AddressBlockedError(hostname, address);
  }
}
