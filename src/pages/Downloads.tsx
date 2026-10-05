import { useMemo } from "react";
import { motion } from "framer-motion";
import {
  Check,
  ChevronDown,
  Download,
  FileArchive,
  FolderTree,
  PackageOpen,
} from "lucide-react";
import { GROUPS, PROJECT_FILES, type GroupKey } from "./fileList";

/**
 * /downloads — every project file as a REAL hosted static file.
 *
 * The complete project is copied into public/source/ (served verbatim by the
 * web server), so every download is a plain HTTP link — no blob tricks, no
 * sandbox issues:
 *
 *   • complete ZIP   →  /source/SecureCam-complete.zip
 *   • single file    →  /source/<exact repo path>
 *   • view raw       →  opens the file in a new tab
 *
 * The listing metadata (labels, groups) comes from fileList.ts; the files
 * themselves live as real static copies under public/source/ and are the
 * exact bytes committed to the repo.
 */

const BASE = "/source";
const ZIP_URL = `${BASE}/SecureCam-complete.zip`;

function LangBadge({ lang }: { lang: string }) {
  return (
    <span className="shrink-0 rounded border border-border px-1.5 py-0.5 font-mono text-[10px] leading-none text-muted-foreground">
      {lang}
    </span>
  );
}

/** Visual tree of the ZIP contents. */
function FileTree() {
  const lines = useMemo(() => {
    const sorted = [...PROJECT_FILES.map((f) => f.path)]
      .sort()
      .map((p) => {
        const depth = p.split("/").length - 1;
        const name = p.split("/").pop() ?? p;
        const isDir = PROJECT_FILES.some((f) => f.path.startsWith(p + "/"));
        return { p, depth, name, isDir };
      });
    return sorted;
  }, []);

  return (
    <div className="overflow-x-auto rounded-lg border border-border bg-paper-dim/40 p-5">
      <pre className="font-mono text-[11.5px] leading-5 text-foreground/90">
        {lines.map(({ p, depth, name, isDir }) => (
          <div key={p} className="whitespace-pre">
            <span className="select-none text-border">
              {"  ".repeat(depth)}
              {depth > 0 ? "└─ " : ""}
            </span>
            <span className={isDir ? "font-medium" : ""}>
              {name}
              {isDir ? "/" : ""}
            </span>
          </div>
        ))}
      </pre>
      <p className="mt-3 flex items-center gap-1.5 text-[11px] text-muted-foreground">
        <PackageOpen className="size-3.5" />
        {PROJECT_FILES.length} key files shown · the ZIP contains every file
          in the repository
      </p>
    </div>
  );
}

const fadeUp = {
  initial: { opacity: 0, y: 16 },
  whileInView: { opacity: 1, y: 0 },
  viewport: { once: true, margin: "-60px" },
  transition: { duration: 0.5, ease: [0.22, 1, 0.36, 1] as const },
};

export default function Downloads() {
  const grouped = useMemo(
    () =>
      GROUPS.map((g) => ({
        ...g,
        files: PROJECT_FILES.filter((f) => f.group === g.key),
      })),
    [],
  );

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

      {/* Hero */}
      <section className="mx-auto max-w-6xl px-6 pb-12 pt-16">
        <motion.div {...fadeUp}>
          <p className="text-[11px] font-medium uppercase tracking-[0.2em] text-muted-foreground">
            Project files
          </p>
          <h1 className="mt-3 text-4xl font-light tracking-tight md:text-5xl">
            Download the source.
            <br />
            Real files, real ZIP.
          </h1>
          <p className="mt-5 max-w-2xl text-[15px] leading-7 text-muted-foreground">
            Every file is hosted as a plain static file — the download buttons
            are ordinary links that always work. Grab everything at once, or
            take single files at their exact repository paths.
          </p>

          {/* Primary actions — plain links to real static files */}
          <div className="mt-8 flex flex-wrap items-center gap-4">
            <a
              href={ZIP_URL}
              download="SecureCam-complete.zip"
              className="inline-flex items-center gap-2 rounded-full bg-foreground px-6 py-2.5 text-sm font-medium text-background transition-opacity hover:opacity-85"
            >
              <FileArchive className="size-4" />
              Download complete ZIP
            </a>
            <a
              href={ZIP_URL}
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-2 rounded-full border border-border px-5 py-2.5 text-sm text-muted-foreground transition-colors hover:border-foreground/40 hover:text-foreground"
            >
              <Download className="size-4" />
              Open ZIP in new tab
            </a>
          </div>

          <div className="mt-4 flex flex-wrap gap-x-6 gap-y-2 text-[12px] text-muted-foreground">
            <span className="flex items-center gap-1.5">
              <Check className="size-3.5 text-foreground" />
              129 files · 290 KB
            </span>
            <span className="flex items-center gap-1.5">
              <Check className="size-3.5 text-foreground" />
              gradle-wrapper.jar included (binary-safe)
            </span>
            <span className="flex items-center gap-1.5">
              <Check className="size-3.5 text-foreground" />
              unzip → <code className="rounded bg-muted px-1 py-0.5">cd android && ./gradlew assembleDebug</code>
            </span>
          </div>
        </motion.div>
      </section>

      {/* Tree */}
      <section className="mx-auto max-w-6xl px-6 pb-14">
        <motion.div {...fadeUp}>
          <h2 className="mb-4 text-[11px] font-medium uppercase tracking-[0.2em] text-muted-foreground">
            Complete structure
          </h2>
          <FileTree />
        </motion.div>
      </section>

      {/* Per-file downloads */}
      <section className="mx-auto max-w-6xl px-6 pb-24">
        {grouped.map((group) => (
          <motion.div key={group.key} {...fadeUp} className="mb-10">
            <button
              type="button"
              onClick={(e) => {
                const el = e.currentTarget;
                const list = el.nextElementSibling as HTMLElement | null;
                if (list) list.style.display = list.style.display === "none" ? "" : "none";
                el.querySelector("svg")?.classList.toggle("-rotate-90");
              }}
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
                <ChevronDown className="size-4 text-muted-foreground transition-transform" />
              </span>
            </button>

            <ul className="divide-y divide-border/70">
              {group.files.map((file) => {
                const base = file.path.split("/").pop() ?? file.path;
                return (
                  <li
                    key={file.path}
                    className="flex flex-col gap-3 py-4 md:flex-row md:items-center md:justify-between"
                  >
                    <div className="min-w-0">
                      <div className="flex items-center gap-2.5">
                        <LangBadge lang={file.lang.toUpperCase()} />
                        <code className="truncate text-[13px] font-medium">
                          {file.path}
                        </code>
                      </div>
                      <p className="mt-1 pl-0.5 text-[12px] text-muted-foreground">
                        {file.label}
                      </p>
                    </div>
                    <div className="flex shrink-0 items-center gap-2">
                      <a
                        href={`${BASE}/${file.path}`}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex items-center gap-1.5 rounded-full border border-border px-3 py-1 text-[12px] text-muted-foreground transition-colors hover:border-foreground/40 hover:text-foreground"
                      >
                        View
                      </a>
                      <a
                        href={`${BASE}/${file.path}`}
                        download={base}
                        className="inline-flex items-center gap-1.5 rounded-full border border-border px-3 py-1 text-[12px] text-foreground transition-colors hover:border-foreground/50"
                      >
                        <Download className="size-3.5" />
                        Download
                      </a>
                    </div>
                  </li>
                );
              })}
            </ul>
          </motion.div>
        ))}

        <motion.div {...fadeUp} className="rounded-lg border border-border bg-paper-dim/40 p-6">
          <div className="flex items-start gap-3">
            <FolderTree className="mt-0.5 size-4 text-muted-foreground" />
            <div className="text-[13px] leading-6 text-muted-foreground">
              <p className="font-medium text-foreground">After unzipping</p>
              <p className="mt-1">
                Open <code className="text-foreground">android/</code> in
                Android Studio, or run
                <code className="mx-1 rounded bg-muted px-1.5 py-0.5 text-foreground">cd android &amp;&amp; ./gradlew assembleDebug</code>
                — the APK appears in
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
