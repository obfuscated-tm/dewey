import { test } from "node:test";
import assert from "node:assert/strict";
import os from "node:os";
import path from "node:path";
import fs from "node:fs/promises";
import { TrustedHosts, hostAllowed, hostOf, urlsIn } from "../src/trustedhosts.js";

const tmpFile = async () => path.join(await fs.mkdtemp(path.join(os.tmpdir(), "schoology-mcp-trusted-")), "trusted-hosts.json");

test("urlsIn finds URLs in markdown, arrays and objects, without trailing punctuation", () => {
  const urls = urlsIn({
    body_md: "Read [this](https://www.example.org/a?b=1). Also https://lessons.example.net/x, then stop.",
    links: [{ url: "http://plain.example.com/" }],
    n: 3,
  });
  assert.deepEqual(urls, ["https://www.example.org/a?b=1", "https://lessons.example.net/x", "http://plain.example.com/"]);
});

test("hostOf lowercases, drops www., and refuses non-http schemes", () => {
  assert.equal(hostOf("https://WWW.Example.org/x"), "example.org");
  assert.equal(hostOf("javascript:alert(1)"), null);
  assert.equal(hostOf("not a url"), null);
});

test("learn skips Schoology and Google Docs/Drive, and persists the rest", async () => {
  const file = await tmpFile();
  const t = new TrustedHosts({ file });
  await t.learn(
    ["https://fuhsd.schoology.com/course/1", "https://docs.google.com/document/d/x", "https://drive.google.com/x", "https://codingbat.com/java"],
    "material 1",
  );
  assert.deepEqual([...t.hosts.keys()], ["codingbat.com"]);
  const again = await new TrustedHosts({ file }).load();
  assert.equal(again.has("www.codingbat.com"), true);
  assert.equal(again.hosts.get("codingbat.com").from, "material 1");
});

test("hostAllowed: exact host or a subdomain, from learned hosts or sites.json", async () => {
  const t = new TrustedHosts({ file: await tmpFile() });
  await t.learn("https://apcs.example.edu/lesson", "x");
  const sites = [{ course: "French", url: "https://www.grammar.example.org/" }];
  for (const h of ["apcs.example.edu", "www.apcs.example.edu", "a.apcs.example.edu", "grammar.example.org", "www.grammar.example.org"]) {
    assert.equal(hostAllowed(h, { trusted: t, sites }), true, `${h} should be allowed`);
  }
  for (const h of ["example.edu", "evilapcs.example.edu", "apcs.example.edu.evil.com", "evil.com", ""]) {
    assert.equal(hostAllowed(h, { trusted: t, sites }), false, `${h} should be refused`);
  }
});
