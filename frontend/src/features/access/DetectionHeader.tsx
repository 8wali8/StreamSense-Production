import { useEffect, useLayoutEffect, useRef, useState } from "react";
import {
  confidenceFor,
  IDLE_STEPS,
  idleTarget,
  letterTarget,
  nearestLetter,
  type Rect,
  type Target,
  THIN_FROM,
  WORD,
  wordTarget,
} from "./detection-header";

const IDLE_MS = 700;

function prefersReducedMotion(): boolean {
  return typeof window.matchMedia === "function" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

/**
 * The wordmark with the sponsor detector's bounding box on it. Left alone it scans the letters and
 * rests on the word; near a pointer it snaps to the letter underneath with a confidence that reads off
 * the distance. Decorative: the accessible name of the page comes from the visually hidden heading.
 */
export function DetectionHeader() {
  const host = useRef<HTMLDivElement>(null);
  const letters = useRef<Array<HTMLSpanElement | null>>([]);
  const [rects, setRects] = useState<Rect[]>([]);
  const [pointerX, setPointerX] = useState<number | null>(null);
  const [step, setStep] = useState(WORD.length);
  const [reduced] = useState(prefersReducedMotion);

  useLayoutEffect(() => {
    function measure() {
      const frame = host.current?.getBoundingClientRect();
      if (!frame) return;
      setRects(
        letters.current.map((el) => {
          const r = el?.getBoundingClientRect();
          return r
            ? { x: r.left - frame.left, y: r.top - frame.top, w: r.width, h: r.height }
            : { x: 0, y: 0, w: 0, h: 0 };
        }),
      );
    }
    measure();
    // Letter widths change once IBM Plex has loaded; jsdom has no `document.fonts`.
    void document.fonts?.ready.then(measure);
    window.addEventListener("resize", measure);
    return () => window.removeEventListener("resize", measure);
  }, []);

  useEffect(() => {
    if (reduced || pointerX !== null) return;
    const timer = window.setInterval(() => setStep((s) => (s + 1) % IDLE_STEPS), IDLE_MS);
    return () => window.clearInterval(timer);
  }, [reduced, pointerX]);

  let target: Target | null = null;
  if (rects.length === WORD.length && rects[0].w > 0) {
    if (pointerX !== null) {
      const { index, distance } = nearestLetter(rects, pointerX);
      target = letterTarget(rects, index, confidenceFor(distance));
    } else {
      target = reduced ? wordTarget(rects, "0.97") : idleTarget(rects, step);
    }
  }

  return (
    <div
      ref={host}
      className="detect-header"
      aria-hidden="true"
      onPointerMove={(event) => {
        const frame = host.current?.getBoundingClientRect();
        if (frame) setPointerX(event.clientX - frame.left);
      }}
      onPointerLeave={() => setPointerX(null)}
    >
      <i className="frame-corner tl" />
      <i className="frame-corner tr" />
      <i className="frame-corner bl" />
      <i className="frame-corner br" />
      <div className="wordmark">
        {[...WORD].map((letter, index) => (
          <span
            // Letters are fixed for the life of the component; the index is the identity.
            key={index}
            ref={(el) => {
              letters.current[index] = el;
            }}
            className={[index >= THIN_FROM ? "thin" : "", target?.letter === index ? "hit" : ""].join(" ").trim()}
          >
            {letter}
          </span>
        ))}
      </div>
      {target && (
        <div
          className="detect-box"
          style={{ left: target.box.x, top: target.box.y, width: target.box.w, height: target.box.h }}
        >
          <i />
          <i />
          <i />
          <i />
          <span className="detect-tag">{target.label}</span>
        </div>
      )}
    </div>
  );
}
