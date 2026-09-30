import { useMemo, useState } from "react";
import { motion } from "framer-motion";
import {
  Check,
  ChevronDown,
  Copy,
  Download,
  FileArchive,
  FileCode,
  FolderTree,
  Loader2,
  PackageOpen,
} from "lucide-react";
import {
  GROUPS,
  PROJECT_FILES,
  WRAPPER_JAR_PATH,
  WRAPPER_JAR_URL,
  type GroupKey,
  type ProjectFile,
} from "./projectFiles";

/**
 * /downloads — every project file with its correct repository path,
 * a per-file download button, and a one-click "download everything as ZIP"
 * (web + android/ + .github/ + README + .gitignore), built with JSZip.
 *
 * Byte-accuracy: file contents are inlined at build time via Vite raw
 * imports, so downloads match the repository exactly. The one binary file
 * (gradle-wrapper.jar) is fetched from Gradle's repo when the ZIP is built.
 */

// ── Download helpers ─────────────────────────────────────────────────────

function triggerDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

/** Downloads a single file using only its basename. */
function downloadSingle(file: ProjectFile) {
  const basename = file.path.split("/").pop() ?? file.path;
  triggerDownload(new Blob([file.content], { type: "text/plain;charset=utf-8" }), basename);
}

function downloadText(text: string, filename: string, mime = "text/plain;charset=utf-8") {
  triggerDownload(new Blob([text], { type: mime }), filename);
}

/** Builds the complete project ZIP (async: fetches the wrapper JAR). */
async function buildProjectZip(): Promise<Blob> {
  const JSZip = (await import("jszip")).default;
  const zip = new JSZip();

  // Every text file at its exact repository path.
  for (const file of PROJECT_FILES) {
    zip.file(file.path, file.content);
  }

  // Binary wrapper JAR — fetched fresh so it stays uncorrupted.
  try {
    const res = await fetch(WRAPPER_JAR_URL);
    if (res.ok) {
      zip.file(WRAPPER_JAR_PATH, await res.arrayBuffer());
    }
    // If the fetch fails, the ZIP still builds; the Gradle wrapper will
    // simply download the JAR itself on first run.
  } catch {
    // Non-fatal (see comment above).
  }

  return zip.generateAsync({ type: "blob", compression: "DEFLATE" });
}

// ── Small UI pieces ──────────────────────────────────────────────────────

const LANG_BADGE: Record<ProjectFile["lang"], string> = {
  kotlin: "KT",
  xml: "XML",
  gradle: "KTS",
  yaml: "YML",
  md: "MD",
  properties: "CFG",
  tsx: "TSX",
  html: "HTML",
  css: "CSS",
  svg: "SVG",
};

function LangBadge({ lang }: { lang: ProjectFile["lang"] }) {
  return (
    <span className="shrink-0 rounded border border-border px-1.5 py-0.5 font-mono text-[10px] leading-none text-muted-foreground">
      {LANG_BADGE[lang]}
    </span>
  );
}

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);
  return (
    <button
      onClick={() => {
        navigator.clipboard.writeText(text).then(
          () => {
            setCopied(true);
            window.setTimeout(() => setCopied(false), 1500);
          },
          () => undefined,
        );
      }}
      className="inline-flex items-center gap-1.5 rounded-full border border-border px-3 py-1 text-[12px] text-muted-foreground transition-colors hover:border-foreground/40 hover:text-foreground"
      aria-label="Copy file contents"
    >
      {copied ? <Check className="size-3.5" /> : <Copy className="size-3.5" />}
      {copied ? "Copied" : "Copy"}
    </button>
  );
}

// ── File tree (visual) ───────────────────────────────────────────────────

function FileTree() {
  const lines = useMemo(() => {
    const paths = PROJECT_FILES.map((f) => f.path).concat([
      "android/gradle/wrapper/gradle-wrapper.jar",
      "android/app/src/main/res/values/themes.xml",
    ]);
    const sorted = [...new Set(paths)].sort();
    return sorted.map((p) => {
      const depth = p.split("/").length - 1;
      const name = p.split("/").pop() ?? p;
      const isDir =
        sorted.some((other) => other.startsWith(p + "/"));
      return { p, depth, name, isDir };
    });
  }, []);

  return (
    <div className="overflow-x-auto rounded-lg border border-border bg-paper-dim/40 p-5">
      <pre className="font-mono text-[11.5px] leading-5 text-foreground/90">
        {lines.map(({ p, depth, name, isDir }) => (
          <div key={p} className="whitespace-pre">
            <span className="select-none text-border">{"  ".repeat(depth)}{depth > 0 ? "└─ " : ""}</span>
            <span className={isDir ? "font-medium" : ""}>{name}{isDir ? "/" : ""}</span>
          </div>
        ))}
      </pre>
      <p className="mt-3 text-[11px] text-muted-foreground">
        {PROJECT_FILES.length + 1} files · complete tree preserved inside the ZIP
      </p>
    </div>
  );
}

// ── Page ─────────────────────────────────────────────────────────────────

const fadeUp = {
  initial: { opacity: 0, y: 16 },
  whileInView: { opacity: 1, y: 0 },
  viewport: { once: true, margin: "-60px" },
  transition: { duration: 0.5, ease: [0.22, 1, 0.36, 1] as const },
};

export default function Downloads() {
  const [zipState, setZipState] = useState<"idle" | "building" | "done">("idle");
  const [openGroups, setOpenGroups] = useState<Record<GroupKey, boolean>>({
    kotlin: true,
    resources: true,
    gradle: true,
    ci: true,
    web: false,
  });

  const grouped = useMemo(() => {
    return GROUPS.map((g) => ({
      ...g,
      files: PROJECT_FILES.filter((f) => f.group === g.key),
    }));
  }, []);

  const toggleGroup = (key: GroupKey) =>
    setOpenGroups((prev) => ({ ...prev, [key]: !prev[key] }));

  const handleZip = async () => {
    setZipState("building");
    try {
      const blob = await buildProjectZip();
      triggerDownload(blob, "SecureCam-complete.zip");
      setZipState("done");
    } catch {
      setZipState("idle");
    }
  };

  return (
    <div className="min-h-screen bg-background text-foreground antialiased">
      {/* Nav */}
      <header className="sticky top-0 z-20 border-b border-border/60 bg-background/90 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-6xl items-center justify-between px-6">
          <a href="/" className="flex items-center gap-2.5">
            <span className="flex size-6 items-center justify-center rounded-full bg-foreground">
              <span className="size-2 rounded-full bg-background" />
            </span>
            <span className="text-sm font-medium tracking-tight">SecureCam</span>
          </a>
          <nav className="flex items-center gap-8 text-[13px] text-muted-foreground">
            <a href="/#features" className="transition-colors hover:text-foreground">Features</a>
            <a href="/#privacy" className="transition-colors hover:text-foreground">Privacy</a>
            <a href="/#build" className="transition-colors hover:text-foreground">Build</a>
            <span className="font-medium text-foreground">Downloads</span>
          </nav>
        </div>
      </header>

      {/* Header */}
      <section className="mx-auto max-w-6xl px-6 pb-10 pt-16">
        <motion.div {...fadeUp}>
          <p className="text-[11px] font-medium uppercase tracking-[0.2em] text-muted-foreground">
            Project files
          </p>
          <h1 className="mt-3 text-4xl font-light tracking-tight md:text-5xl">
            Download the source.
            <br />
            Every file, correct path.
          </h1>
          <p className="mt-5 max-w-2xl text-[15px] leading-7 text-muted-foreground">
            Each file below is served byte-identical from the repository with
            its exact path — download individually, or grab the whole tree as
            a ZIP that builds with
            <code className="mx-1 rounded bg-muted px-1.5 py-0.5 text-[13px] text-foreground">./gradlew assembleDebug</code>
            right after unzipping.
          </p>

          <div className="mt-8 flex flex-wrap items-center gap-4">
            <button
              onClick={handleZip}
              disabled={zipState === "building"}
              className="inline-flex items-center gap-2 rounded-full bg-foreground px-6 py-2.5 text-sm font-medium text-background transition-opacity hover:opacity-85 disabled:opacity-60"
            >
              {zipState === "building" ? (
                <>
                  <Loader2 className="size-4 animate-spin" />
                  Building ZIP…
                </>
              ) : zipState === "done" ? (
                <>
                  <Check className="size-4" />
                  Downloaded — click again if needed
                </>
              ) : (
                <>
                  <FileArchive className="size-4" />
                  Download complete ZIP
                </>
              )}
            </button>
            <button
              onClick={() =>
                downloadText(
                  PROJECT_FILES.map((f) => f.path).join("\n"),
                  "SecureCam-file-list.txt",
                )
              }
              className="inline-flex items-center gap-2 rounded-full border border-border px-5 py-2.5 text-sm text-muted-foreground transition-colors hover:border-foreground/40 hover:text-foreground"
            >
              <FolderTree className="size-4" />
              File list (.txt)
            </button>
          </div>
          <p className="mt-3 flex items-center gap-1.5 text-[12px] text-muted-foreground">
            <PackageOpen className="size-3.5" />
            ZIP includes android/, .github/, README.md, .gitignore and this web workspace.
          </p>
        </motion.div>
      </section>

      {/* File tree */}
      <section className="mx-auto max-w-6xl px-6 pb-14">
        <motion.div {...fadeUp}>
          <h2 className="mb-4 text-[11px] font-medium uppercase tracking-[0.2em] text-muted-foreground">
            Complete structure
          </h2>
          <FileTree />
        </motion.div>
      </section>

      {/* Per-file listing */}
      <section className="mx-auto max-w-6xl px-6 pb-24">
        {grouped.map((group) => (
          <motion.div key={group.key} {...fadeUp} className="mb-10">
            <button
              onClick={() => toggleGroup(group.key)}
              className="mb-4 flex w-full items-baseline justify-between border-b border-border pb-2 text-left"
            >
              <span className="flex items-baseline gap-3">
                <span className="text-lg font-medium">{group.title}</span>
                <span className="text-[12px] text-muted-foreground">
                  {group.files.length} files
                </span>
              </span>
              <span className="flex items-center gap-3">
                <span className="hidden text-[12px] text-muted-foreground md:inline">
                  {group.blurb}
                </span>
                <ChevronDown
                  className={`size-4 text-muted-foreground transition-transform ${openGroups[group.key] ? "" : "-rotate-90"}`}
                />
              </span>
            </button>

            {openGroups[group.key] && (
              <ul className="divide-y divide-border/70">
                {group.files.map((file) => (
                  <li
                    key={file.path}
                    className="flex flex-col gap-3 py-4 md:flex-row md:items-center md:justify-between"
                  >
                    <div className="min-w-0">
                      <div className="flex items-center gap-2.5">
                        <LangBadge lang={file.lang} />
                        <code className="truncate text-[13px] font-medium">
                          {file.path}
                        </code>
                      </div>
                      <p className="mt-1 pl-0.5 text-[12px] text-muted-foreground">
                        {file.label}
                      </p>
                    </div>
                    <div className="flex shrink-0 items-center gap-2">
                      <CopyButton text={file.content} />
                      <button
                        onClick={() => downloadSingle(file)}
                        className="inline-flex items-center gap-1.5 rounded-full border border-border px-3 py-1 text-[12px] text-foreground transition-colors hover:border-foreground/50"
                      >
                        <Download className="size-3.5" />
                        Download
                      </button>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </motion.div>
        ))}

        <motion.div {...fadeUp} className="rounded-lg border border-border bg-paper-dim/40 p-6">
          <div className="flex items-start gap-3">
            <FileCode className="mt-0.5 size-4 text-muted-foreground" />
            <div className="text-[13px] leading-6 text-muted-foreground">
              <p className="font-medium text-foreground">After unzipping</p>
              <p className="mt-1">
                The Android project lives in <code className="text-foreground">android/</code>.
                Open it in Android Studio, or run
                <code className="mx-1 rounded bg-muted px-1.5 py-0.5 text-foreground">cd android &amp;&amp; ./gradlew assembleDebug</code>
                — the APK lands in
                <code className="mx-1 rounded bg-muted px-1.5 py-0.5 text-foreground">app/build/outputs/apk/debug/</code>.
                Pushing to GitHub runs the included Actions workflow automatically.
              </p>
            </div>
          </div>
        </motion.div>
      </section>

      <footer className="border-t border-border">
        <div className="mx-auto max-w-6xl px-6 py-8 text-[12px] text-muted-foreground">
          SecureCam — records openly, or not at all.
        </div>
      </footer>
    </div>
  );
}
