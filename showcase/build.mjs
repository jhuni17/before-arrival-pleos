import { build } from "esbuild";
import { readFile, writeFile, mkdir } from "node:fs/promises";
import { dirname, resolve } from "node:path";

const output = resolve(process.argv[2] || "before-arrival-showcase.html");
const bundled = await build({
  entryPoints: ["examples/before-arrival/client.tsx"],
  bundle: true,
  write: false,
  outdir: "bundle",
  jsx: "automatic",
  minify: true,
  legalComments: "inline",
  target: "chrome100",
  loader: { ".ttf": "dataurl" },
  nodePaths: (process.env.SHOWCASE_MODULES || "").split(";").filter(Boolean),
  define: { "process.env.NODE_ENV": '"production"' },
});
const js = bundled.outputFiles
  .find((file) => file.path.endsWith(".js"))
  .text.replaceAll("</script", "<\\/script");
const css = bundled.outputFiles.find((file) => file.path.endsWith(".css")).text;
const license = await readFile("LICENSE", "utf8");
const fontLicense = (await readFile("assets/fonts/OFL.txt", "utf8")).replace(
  /[ \t]+$/gm,
  "",
);
const document = `<!doctype html>
<!--
${license}
Asta Sans: original font from google/fonts; source https://github.com/42dot/Asta-Sans
${fontLicense}
-->
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="description" content="남은 시간에 맞춰 소식을 정리하는 ‘도착 전에’의 콘셉트 데모">
<title>도착 전에 · 콘셉트 데모</title>
<style>${css}</style>
</head>
<body>
<div id="root"></div>
<noscript>시연을 보려면 JavaScript를 사용해 주세요.</noscript>
<script>${js}</script>
</body>
</html>`;
await mkdir(dirname(output), { recursive: true });
await writeFile(output, document);
console.log(output);
