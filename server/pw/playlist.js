'use strict';
// Port of lan/LanPlaylist.kt: hls.js fetches every child URI itself, so each
// one has to point back at this proxy.

const URI_TAGS = new Set([
  '#EXT-X-KEY', '#EXT-X-SESSION-KEY', '#EXT-X-MAP', '#EXT-X-MEDIA',
  '#EXT-X-I-FRAME-STREAM-INF', '#EXT-X-SESSION-DATA', '#EXT-X-PART',
  '#EXT-X-PRELOAD-HINT', '#EXT-X-RENDITION-REPORT',
]);
const ATTR = /URI="([^"]*)"/g;

// base is the URL after redirects: a master that 302s to a CDN resolves relative
// segments against the CDN, not against the provider's host
function rewrite(text, base, link) {
  let baseUrl;
  try { baseUrl = new URL(base); } catch (e) { return text; }
  return text.split('\n').map((raw) => {
    const cr = raw.endsWith('\r');
    const line = cr ? raw.slice(0, -1) : raw;
    let out;
    if (!line.trim()) out = line;
    else if (line.startsWith('#')) out = attrs(line, baseUrl, link);
    else out = linkOf(line, baseUrl, link);
    return cr ? out + '\r' : out;
  }).join('\n');
}

function attrs(line, base, link) {
  if (!URI_TAGS.has(line.slice(0, line.indexOf(':')).toUpperCase())) return line;
  return line.replace(ATTR, (_, uri) => 'URI="' + linkOf(uri, base, link) + '"');
}

// EXT-X-BYTERANGE offsets are relative to the segment, so rewriting the URI keeps them valid
function linkOf(raw, base, link) {
  if (!raw.trim()) return raw;
  try { return link(new URL(raw, base).toString()); } catch (e) { return raw; }
}

module.exports = { rewrite };
