import { motion } from "framer-motion";
import {
  Aperture,
  BellRing,
  Camera,
  CircleDot,
  Download,
  Grid3x3,
  Play,
  Share2,
  ShieldCheck,
  Smartphone,
  Timer,
  Video,
} from "lucide-react";

/**
 * SecureCam project landing page — Minimalism theme.
 *
 * Near-monochrome (ink on paper), hairline dividers, generous whitespace,
 * precise alignment. Color appears exactly once: the recording red.
 */

const fadeUp = {
  initial: { opacity: 0, y: 16 },
  whileInView: { opacity: 1, y: 0 },
  viewport: { once: true, margin: "-80px" },
  transition: { duration: 0.55, ease: [0.22, 1, 0.36, 1] as const },
};

const features = [
  {
    icon: Camera,
    title: "Photo, at full quality",
    body: "CameraX ImageCapture in MAXIMIZE_QUALITY mode, saved straight to Pictures/SecureCam.",
  },
  {
    icon: Video,
    title: "Video with sound",
    body: "Front and back, 4:3 · 16:9 · 1:1, with a recording timer and torch control.",
  },
  {
    icon: Aperture,
    title: "Deliberate controls",
    body: "Pinch to zoom, tap to focus, three-second grid overlay, 3/5/10-second self-timer.",
  },
  {
    icon: Grid3x3,
    title: "In-app gallery",
    body: "Everything you capture, in one quiet grid — preview, play, share, delete.",
  },
  {
    icon: Timer,
    title: "Foreground service",
    body: "Background front-camera recording survives a locked screen and a minimized app.",
  },
  {
    icon: BellRing,
    title: "Always announced",
    body: "A persistent, non-dismissable notification for the entire recording. No exceptions.",
  },
];

export default function Landing() {
  return (
    <div className="min-h-screen bg-background text-foreground antialiased">
      {/* ── Nav ─────────────────────────────────────────────────────── */}
      <header className="sticky top-0 z-20 border-b border-border/60 bg-background/90 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-6xl items-center justify-between px-6">
          <a href="#top" className="flex items-center gap-2.5">
            <span className="flex size-6 items-center justify-center rounded-full bg-foreground">
              <CircleDot className="size-3 text-background" strokeWidth={2.5} />
            </span>
            <span className="text-sm font-medium tracking-tight">SecureCam</span>
          </a>
          <nav className="hidden items-center gap-8 text-[13px] text-muted-foreground md:flex">
            <a href="#features" className="transition-colors hover:text-foreground">
              Features
            </a>
            <a href="#privacy" className="transition-colors hover:text-foreground">
              Privacy
            </a>
            <a href="#design" className="transition-colors hover:text-foreground">
              Design
            </a>
            <a href="#build" className="transition-colors hover:text-foreground">
              Build
            </a>
          </nav>
          <a
            href="#build"
            className="rounded-full bg-foreground px-4 py-1.5 text-[13px] font-medium text-background transition-opacity hover:opacity-85"
          >
            Get the APK
          </a>
        </div>
      </header>

      {/* ── Hero ────────────────────────────────────────────────────── */}
      <section id="top" className="mx-auto max-w-6xl px-6 pb-24 pt-20 md:pt-28">
        <div className="grid items-center gap-16 md:grid-cols-[1.2fr_0.8fr]">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }}
          >
            <p className="mb-6 text-[11px] font-medium uppercase tracking-[0.2em] text-muted-foreground">
              Android · Kotlin · CameraX · Compose
            </p>
            <h1 className="max-w-xl text-5xl font-light leading-[1.05] tracking-tight md:text-6xl">
              A camera app with
              <br />
              nothing to hide.
            </h1>
            <p className="mt-6 max-w-md text-[15px] leading-7 text-muted-foreground">
              SecureCam is a production-ready Android camera with one defining
              feature: front-camera recording that keeps working in the
              background — and always tells you it&rsquo;s there.
            </p>
            <div className="mt-10 flex flex-wrap items-center gap-4">
              <a
                href="#build"
                className="inline-flex items-center gap-2 rounded-full bg-foreground px-6 py-2.5 text-sm font-medium text-background transition-opacity hover:opacity-85"
              >
                <Download className="size-4" />
                Download the APK
              </a>
              <a
                href="#privacy"
                className="text-sm text-muted-foreground underline-offset-4 transition-colors hover:text-foreground hover:underline"
              >
                Read the privacy contract
              </a>
            </div>
            <dl className="mt-14 grid max-w-md grid-cols-3 gap-6 border-t border-border pt-6 text-[13px]">
              <div>
                <dt className="text-muted-foreground">minSdk</dt>
                <dd className="mt-1 font-medium">Android 8.0</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">targetSdk</dt>
                <dd className="mt-1 font-medium">Android 15</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">CameraX</dt>
                <dd className="mt-1 font-medium">1.4 stable</dd>
              </div>
            </dl>
          </motion.div>

          {/* Phone mockup — pure CSS, minimalism style */}
          <motion.div
            initial={{ opacity: 0, scale: 0.97 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.7, delay: 0.15, ease: [0.22, 1, 0.36, 1] }}
            className="justify-self-center"
          >
            <div className="relative aspect-[9/19.5] w-60 rounded-[2.2rem] border border-border bg-ink p-2 shadow-[0_24px_60px_-24px_rgba(0,0,0,0.45)]">
              <div className="relative flex h-full w-full flex-col overflow-hidden rounded-[1.8rem] bg-neutral-900">
                {/* viewfinder */}
                <div className="relative flex-1">
                  {/* grid lines */}
                  <div className="absolute inset-0 opacity-25">
                    <div className="absolute left-1/3 top-0 h-full w-px bg-white/60" />
                    <div className="absolute left-2/3 top-0 h-full w-px bg-white/60" />
                    <div className="absolute left-0 top-1/3 h-px w-full bg-white/60" />
                    <div className="absolute left-0 top-2/3 h-px w-full bg-white/60" />
                  </div>
                  {/* status row */}
                  <div className="absolute inset-x-0 top-0 flex items-center justify-between px-4 pt-4">
                    <span className="size-1.5 rounded-full bg-record-red" />
                    <span className="text-[9px] font-medium tracking-widest text-white/70">
                      1.0x
                    </span>
                  </div>
                  {/* recording pill */}
                  <div className="absolute inset-x-0 top-8 flex justify-center">
                    <span className="rounded-full bg-black/40 px-2.5 py-1 text-[9px] font-medium text-white">
                      BG_0023.mp4 · 04:12
                    </span>
                  </div>
                  {/* shutter */}
                  <div className="absolute inset-x-0 bottom-5 flex justify-center">
                    <span className="flex size-11 items-center justify-center rounded-full border-2 border-white/90">
                      <span className="size-8 rounded-full bg-white/90" />
                    </span>
                  </div>
                </div>
              </div>
            </div>
            <p className="mt-4 text-center text-[11px] text-muted-foreground">
              Background recording · notification always visible
            </p>
          </motion.div>
        </div>
      </section>

      {/* ── Features ────────────────────────────────────────────────── */}
      <section id="features" className="border-t border-border">
        <div className="mx-auto max-w-6xl px-6 py-20">
          <motion.div {...fadeUp} className="mb-14 flex items-end justify-between">
            <div>
              <p className="text-[11px] font-medium uppercase tracking-[0.2em] text-muted-foreground">
                01 — Features
              </p>
              <h2 className="mt-3 text-3xl font-light tracking-tight md:text-4xl">
                Everything a camera should do.
                <br />
                Nothing it shouldn&rsquo;t.
              </h2>
            </div>
          </motion.div>

          <div className="grid gap-x-10 gap-y-12 sm:grid-cols-2 lg:grid-cols-3">
            {features.map((f, i) => (
              <motion.div key={f.title} {...fadeUp} transition={{ ...fadeUp.transition, delay: i * 0.05 }}>
                <f.icon className="size-5 text-foreground" strokeWidth={1.5} />
                <h3 className="mt-4 text-[15px] font-medium">{f.title}</h3>
                <p className="mt-2 max-w-xs text-[13px] leading-6 text-muted-foreground">
                  {f.body}
                </p>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      {/* ── Privacy ─────────────────────────────────────────────────── */}
      <section id="privacy" className="border-t border-border bg-ink text-paper">
        <div className="mx-auto grid max-w-6xl gap-14 px-6 py-24 md:grid-cols-[1fr_1fr]">
          <motion.div {...fadeUp}>
            <p className="text-[11px] font-medium uppercase tracking-[0.2em] text-paper/50">
              02 — Privacy
            </p>
            <h2 className="mt-3 text-3xl font-light leading-tight tracking-tight md:text-4xl">
              If it records,
              <br />
              you&rsquo;ll know.
            </h2>
            <p className="mt-6 max-w-md text-[15px] leading-7 text-paper/60">
              Background video is a sensitive capability, so SecureCam treats
              the notification as part of the feature — not an obstacle around
              it. The service is architecturally incapable of recording
              silently.
            </p>
          </motion.div>

          <motion.ul {...fadeUp} className="space-y-6 text-[14px] leading-6">
            {[
              "A persistent, non-dismissable notification appears the instant the service starts — before the camera is even bound.",
              "It shows a recording icon, an elapsed-time chronometer, and two actions: Stop Recording and Open App.",
              "Recording stops only from the in-app button or the notification; the file is saved, confirmed for 3 seconds, then the service exits.",
              "Camera and microphone access run through a typed Android 14+ foreground service, visible in the system privacy indicators.",
            ].map((line, i) => (
              <li key={i} className="flex gap-4 border-b border-paper/10 pb-6">
                <ShieldCheck className="mt-0.5 size-4 shrink-0 text-paper/70" strokeWidth={1.5} />
                <span className="text-paper/80">{line}</span>
              </li>
            ))}
          </motion.ul>
        </div>
      </section>

      {/* ── Design ──────────────────────────────────────────────────── */}
      <section id="design" className="border-t border-border">
        <div className="mx-auto max-w-6xl px-6 py-20">
          <motion.div {...fadeUp}>
            <p className="text-[11px] font-medium uppercase tracking-[0.2em] text-muted-foreground">
              03 — Design
            </p>
            <h2 className="mt-3 max-w-lg text-3xl font-light tracking-tight md:text-4xl">
              Minimalism, applied to every surface.
            </h2>
          </motion.div>

          <motion.div {...fadeUp} className="mt-12 grid gap-10 md:grid-cols-[1fr_1.2fr]">
            <p className="max-w-md text-[14px] leading-7 text-muted-foreground">
              Ink on paper, hairline dividers, small caps labels, and one
              reserved accent. The viewfinder sits on near-black so the image
              is the hero; the gallery sits on off-white with precise
              alignment. Nothing decorates. Everything signals.
            </p>
            <div className="grid grid-cols-4 overflow-hidden rounded-lg border border-border text-[11px]">
              {[
                { name: "Ink", hex: "#0A0A0A", cls: "bg-ink text-paper" },
                { name: "Paper", hex: "#FAFAF8", cls: "bg-paper text-ink border-l border-border" },
                { name: "Divider", hex: "#E5E5E2", cls: "bg-divider text-ink border-l border-border" },
                { name: "Record Red", hex: "#C7362B", cls: "bg-record-red text-paper border-l border-border" },
              ].map((sw) => (
                <div key={sw.name} className="flex h-28 flex-col justify-between p-3">
                  <span className={`inline-block size-3 rounded-full border border-current opacity-30 ${sw.cls}`} />
                  <div className={sw.cls}>
                    <div className="font-medium">{sw.name}</div>
                    <div className="opacity-60">{sw.hex}</div>
                  </div>
                </div>
              ))}
            </div>
          </motion.div>
        </div>
      </section>

      {/* ── Build ───────────────────────────────────────────────────── */}
      <section id="build" className="border-t border-border bg-paper-dim/60">
        <div className="mx-auto max-w-6xl px-6 py-20">
          <motion.div {...fadeUp} className="grid items-center gap-12 md:grid-cols-2">
            <div>
              <p className="text-[11px] font-medium uppercase tracking-[0.2em] text-muted-foreground">
                04 — Build
              </p>
              <h2 className="mt-3 text-3xl font-light tracking-tight md:text-4xl">
                CI builds the APK for you.
              </h2>
              <p className="mt-6 max-w-md text-[14px] leading-7 text-muted-foreground">
                Push to <code className="text-foreground">main</code> and a
                GitHub Actions workflow assembles debug and release APKs with
                JDK 17 and the Android SDK. Every tagged release
                (<code className="text-foreground">v1.0.0</code>…) gets the
                artifacts attached automatically.
              </p>
              <ol className="mt-8 space-y-3 text-[13px] text-muted-foreground">
                <li className="flex gap-3">
                  <span className="font-medium text-foreground">1.</span>
                  Open the repo&rsquo;s <span className="text-foreground">Actions</span> tab
                </li>
                <li className="flex gap-3">
                  <span className="font-medium text-foreground">2.</span>
                  Download <span className="text-foreground">SecureCam-debug-apk</span> from artifacts
                </li>
                <li className="flex gap-3">
                  <span className="font-medium text-foreground">3.</span>
                  Install, grant camera + mic, and you&rsquo;re recording
                </li>
              </ol>
            </div>
            <div className="overflow-hidden rounded-lg border border-border bg-ink">
              <div className="flex items-center gap-1.5 border-b border-paper/10 px-4 py-3">
                <span className="size-2 rounded-full bg-paper/20" />
                <span className="size-2 rounded-full bg-paper/20" />
                <span className="size-2 rounded-full bg-paper/20" />
                <span className="ml-2 text-[11px] text-paper/40">terminal</span>
              </div>
              <pre className="overflow-x-auto p-5 text-[12.5px] leading-6 text-paper/85">
                <code>{`$ git clone <your-repo> && cd <repo>/android
$ ./gradlew assembleDebug

# → app/build/outputs/apk/debug/app-debug.apk`}</code>
              </pre>
            </div>
          </motion.div>
        </div>
      </section>

      {/* ── Footer ──────────────────────────────────────────────────── */}
      <footer className="border-t border-border">
        <div className="mx-auto flex max-w-6xl flex-col items-start justify-between gap-6 px-6 py-10 text-[12px] text-muted-foreground md:flex-row md:items-center">
          <div className="flex items-center gap-2">
            <span className="flex size-5 items-center justify-center rounded-full bg-foreground">
              <CircleDot className="size-2.5 text-background" strokeWidth={2.5} />
            </span>
            <span className="font-medium text-foreground">SecureCam</span>
            <span className="text-border">|</span>
            <span>Records openly, or not at all.</span>
          </div>
          <div className="flex items-center gap-6">
            <span>Kotlin 2.0 · Compose M3 · CameraX 1.4</span>
            <span className="flex items-center gap-1.5">
              <Smartphone className="size-3.5" strokeWidth={1.5} /> Android 8.0+
            </span>
          </div>
        </div>
      </footer>
    </div>
  );
}
