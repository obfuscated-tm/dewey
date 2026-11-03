// Which web hosts fetch_page may open (docs/mcp/MCP-BRIDGE.md, "Course
// websites"): a course site from sites.json, or a host a teacher linked in a
// material or assignment that a live tool returned. Anything else is refused,
// so text in a post or page can't get Claude to send the user's data to a URL
// of its choosing. Learned hosts are kept in ~/.schoology-mcp/trusted-hosts.json.
import fs from "node:fs/promises";
import path from "node:path";
import { MCP_HOME } from "./config.js";
import { log } from "./log.js";

export const TRUSTED_PATH = path.join(MCP_HOME, "trusted-hosts.json");

const URL_RE = /https?:\/\/[^\s<>"'`)\]]+/gi;
// Google Docs/Drive go through the extension's `file` op instead (tools.js),
// and Schoology needs the user's cookies, which fetch_page never sends.
const NOT_WEB = /(^|\.)(schoology\.com|docs\.google\.com|drive\.google\.com)$/;

/** A URL's host, lowercased and without `www.`; null for anything but http(s). */
export function hostOf(url) {
  try {
    const u = new URL(url);
    if (u.protocol !== "http:" && u.protocol !== "https:") return null;
    return normalHost(u.hostname);
  } catch {
    return null;
  }
}

function normalHost(hostname) {
  return String(hostname || "").toLowerCase().replace(/^\[|\]$/g, "").replace(/^www\./, "");
}

/** Every http(s) URL in a value: strings searched, arrays and objects walked. */
export function urlsIn(value, out = []) {
  if (typeof value === "string") {
    for (const m of value.matchAll(URL_RE)) out.push(m[0].replace(/[.,;:!?]+$/, ""));
  } else if (Array.isArray(value)) {
    for (const v of value) urlsIn(v, out);
  } else if (value && typeof value === "object") {
    for (const v of Object.values(value)) urlsIn(v, out);
  }
  return out;
}

export class TrustedHosts {
  constructor({ file = TRUSTED_PATH } = {}) {
    this.file = file;
    /** @type {Map<string, {from: string, at: string}>} */
    this.hosts = new Map();
  }

  async load() {
    try {
      const data = JSON.parse(await fs.readFile(this.file, "utf8"));
      for (const [host, info] of Object.entries(data.hosts || {})) this.hosts.set(host, info);
    } catch (err) {
      if (err.code !== "ENOENT") log(`trusted hosts: could not read ${this.file}: ${err.message}`);
    }
    return this;
  }

  /** Remember the hosts of every URL in `value` (teacher-written content); `from` says where. */
  async learn(value, from) {
    let added = false;
    for (const url of urlsIn(value)) {
      const host = hostOf(url);
      if (!host || NOT_WEB.test(host) || this.hosts.has(host)) continue;
      this.hosts.set(host, { from, at: new Date().toISOString() });
      added = true;
    }
    if (added) await this.save().catch((err) => log(`trusted hosts: could not save: ${err.message}`));
  }

  has(host) {
    return this.hosts.has(normalHost(host));
  }

  async save() {
    await fs.mkdir(path.dirname(this.file), { recursive: true });
    const tmp = `${this.file}.${process.pid}.tmp`;
    await fs.writeFile(tmp, JSON.stringify({ hosts: Object.fromEntries(this.hosts) }, null, 2), "utf8");
    await fs.rename(tmp, this.file);
  }
}

/**
 * May fetch_page open `hostname`? Yes for a sites.json host, a learned host,
 * or a subdomain of either (www.example.org, lessons.example.org).
 */
export function hostAllowed(hostname, { trusted, sites } = {}) {
  const h = normalHost(hostname);
  if (!h) return false;
  const known = [...(trusted ? trusted.hosts.keys() : []), ...(sites || []).map((s) => hostOf(s.url)).filter(Boolean)];
  return known.some((k) => h === k || h.endsWith(`.${k}`));
}
