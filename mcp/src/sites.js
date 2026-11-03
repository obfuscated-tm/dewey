// sites.json maps courses to their public websites (outside Schoology), e.g.
// AP Comp Sci's lesson site. It is each user's own list, in
// ~/.schoology-mcp/sites.json (add_course_site writes it; see
// sites.example.json for the shape). An older copy at mcp/sites.json is still
// read when there's none there yet. A missing file just means no sites.
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { MCP_HOME } from "./config.js";
import { log } from "./log.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
export const SITES_PATH = path.join(MCP_HOME, "sites.json");
const LEGACY_SITES_PATH = path.join(__dirname, "..", "sites.json");

export async function loadSites(sitesPath = SITES_PATH, legacyPath = sitesPath === SITES_PATH ? LEGACY_SITES_PATH : null) {
  let raw;
  try {
    raw = await fs.readFile(sitesPath, "utf8");
  } catch (err) {
    if (err.code !== "ENOENT") log(`sites: could not read ${sitesPath}: ${err.message}`);
    if (err.code === "ENOENT" && legacyPath) {
      const legacy = await loadSites(legacyPath, null);
      if (legacy.length) log(`sites: using ${legacyPath}; move it to ${sitesPath}`);
      return legacy;
    }
    return [];
  }
  try {
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) {
      log(`sites: ${sitesPath} is not a JSON array; ignoring`);
      return [];
    }
    return parsed.filter((s) => s && typeof s.course === "string" && typeof s.url === "string");
  } catch (err) {
    log(`sites: could not parse ${sitesPath}: ${err.message}`);
    return [];
  }
}

/** Every site whose `course` matches the course name (case-insensitive fragment, either direction). */
export function sitesForCourse(courseName, sites) {
  if (!courseName || !Array.isArray(sites) || sites.length === 0) return [];
  const nameLower = String(courseName).toLowerCase();
  return sites
    .filter((site) => {
      const siteLower = site.course.toLowerCase();
      return nameLower.includes(siteLower) || siteLower.includes(nameLower);
    })
    .map((site) => ({ course: site.course, url: site.url, note: site.note || null }));
}

const hostKey = (url) => {
  try { return new URL(url).hostname.toLowerCase().replace(/^www\./, ""); } catch { return String(url); }
};

/**
 * Add one course site, replacing an entry for the same course on the same
 * host (a course can have several sites), and write the list back to
 * `sitesPath`. Returns the new list.
 */
export async function saveSite(sites, { course, url, note }, sitesPath = SITES_PATH) {
  const entry = { course: String(course).trim(), url: String(url).trim() };
  if (note && String(note).trim()) entry.note = String(note).trim();
  const same = (s) => s.course.toLowerCase() === entry.course.toLowerCase() && hostKey(s.url) === hostKey(entry.url);
  const next = [...(sites || []).filter((s) => !same(s)), entry];
  await fs.mkdir(path.dirname(sitesPath), { recursive: true });
  const tmp = `${sitesPath}.${process.pid}.tmp`;
  await fs.writeFile(tmp, `${JSON.stringify(next, null, 2)}\n`, "utf8");
  await fs.rename(tmp, sitesPath);
  return next;
}
