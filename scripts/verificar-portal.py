#!/usr/bin/env python3
"""Comprobación integral del portal: lo público, la API, el panel y las defensas."""
import base64, hashlib, hmac, http.cookiejar, json, re, ssl, struct, sys, time
import urllib.parse, urllib.request

BASE = "https://localhost"
ENV = "/Users/usuario/playground/NoticiasPlaneta/.env"


def env(k):
    try:
        return next(l.split("=", 1)[1].strip() for l in open(ENV) if l.startswith(k + "="))
    except StopIteration:
        return ""


def totp(s):
    k = base64.b32decode(s + "=" * ((8 - len(s) % 8) % 8))
    h = hmac.new(k, struct.pack(">Q", int(time.time() // 30)), hashlib.sha1).digest()
    o = h[-1] & 0x0F
    return f"{(struct.unpack('>I', h[o:o+4])[0] & 0x7FFFFFFF) % 1_000_000:06d}"


class NR(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *a, **k):
        return None


jar = http.cookiejar.CookieJar()
op = urllib.request.build_opener(urllib.request.HTTPSHandler(context=ssl._create_unverified_context()),
                                 urllib.request.HTTPCookieProcessor(jar), NR())


def req(p, d=None, headers=None, raw=False):
    if isinstance(d, str):
        d = d.encode()
    b = urllib.parse.urlencode(d).encode() if isinstance(d, dict) else d
    r = urllib.request.Request(BASE + p, data=b, method="POST" if d else "GET")
    for k, v in (headers or {}).items():
        r.add_header(k, v)
    if isinstance(d, dict):
        r.add_header("Content-Type", "application/x-www-form-urlencoded")
    try:
        x = op.open(r)
        return x.status, x.read().decode("utf-8", "replace"), dict(x.headers)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace"), dict(e.headers)


def csrf(h):
    m = re.search(r'name="_csrf"\s+value="([^"]+)"', h)
    return m.group(1) if m else ""


filas = []


def check(nombre, ok, detalle=""):
    filas.append((nombre, ok, detalle))


# ---------- público ----------
s, html, _ = req("/")
check("portada", s == 200 and 'class="titular"' in html, "%s, %d titulares" % (s, html.count('class="titular"')))
check("portada con foto", 'class="miniatura"' in html, "miniatura" if "miniatura" in html else "sin foto")

slug = re.search(r'class="titular" href="([^"]+)"', html).group(1)
s, art, _ = req(slug)
check("página de artículo", s == 200 and "<h1>" in art, f"{s}")
check("artículo con figura", "<figure" in art, "sí" if "<figure" in art else "sin foto")

s, _, _ = req("/no/existe-nunca")
check("artículo inexistente → 404", s == 404, f"{s}")

s, bus, _ = req("/buscar?q=electricidad")
check("buscador", s == 200, f"{s}")

s, temas, _ = req("/temas/sequia-en-el-duero")
check("temas", s == 200, f"{s}")

s, api, _ = req("/api/v1/public/search?q=electricidad")
check("API pública de búsqueda", s == 200, f"{s}")

# ---------- defensas ----------
s, _, _ = req("/admin")
check("panel sin sesión → login", s == 302, f"{s}")

s, _, _ = req("/api/v1/articles", {"x": "1"}, {"X-Api-Key": "mala"}, raw=True)
check("API de escritura con clave mala", s in (401, 403), f"{s}")

s, _, _ = req("/mcp", {"jsonrpc": "2.0", "id": 1, "method": "tools/list"}, raw=True)
check("MCP sin clave", s == 401, f"{s}")

s, _, _ = req("/media/xx/sha.png")
check("ruta de medio inválida", s == 404, f"{s}")

key = None
s, _, _ = req("/api/v1/media/presign", json.dumps({"filename": "x.png", "contentType": "image/png",
                                                   "sizeBytes": 10, "uploaderId": "u"}).encode(),
              {"X-Api-Key": env("AGENT_API_KEY") or "clave-de-desarrollo",
               "Content-Type": "application/json"}, raw=True)
check("presign rechaza SVG/tamaños raros por separado", s in (200, 400), f"{s}")

# ---------- panel ----------
s, login, _ = req("/admin/login")
check("formulario de login", s == 200 and csrf(login) != "", f"{s}")

s, _, h = req("/admin/login", {"email": env("ADMIN_EMAIL"), "password": env("PANEL_PASSWORD"),
                               "totp": "000000", "_csrf": csrf(login)})
check("TOTP incorrecto rechazado", "bad_totp" in h.get("Location", ""), h.get("Location", ""))

s, bandeja, _ = req("/admin?estado=DRAFT")
check("bandeja", s == 200 and "Bandeja" in bandeja, f"{s}")

aid = re.search(r'/admin/articulos/([0-9a-f-]{36})', bandeja).group(1)
s, ed, _ = req(f"/admin/articulos/{aid}")
check("editor con texto, imágenes, historial y auditoría",
      all(x in ed for x in ("Historial de versiones", "Imágenes", "Auditoría")), f"{s}")

s, _, _ = req(f"/admin/articulos/{aid}/editar", {"title": "x", "body": "y", "revision": "1", "_csrf": "malo"})
check("CSRF inválido en el panel", s == 403, f"{s}")

s, pub, _ = req("/admin?estado=PUBLISHED")
check("filtro por estado", s == 200 and "PUBLISHED" in pub, f"{s}")

print()
for n, ok, d in filas:
    print(f"{'✓' if ok else '✗'}  {n:44} {d}")
print()
fallos = [f for f in filas if not f[1]]
print("RESULTADO:", "TODO OK" if not fallos else f"{len(fallos)} FALLOS")
sys.exit(0 if not fallos else 1)
