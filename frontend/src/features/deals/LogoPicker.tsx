import { useId, useState, type DragEvent } from "react";
import { pickLogoFiles } from "./logo-files";

type LogoPickerProps = {
  /** How many more logos the deal can take; at zero the picker explains instead of accepting. */
  remaining: number;
  busy?: boolean;
  onFiles: (files: File[]) => void;
};

/** A drop zone with a file picker inside it: the one way a logo reaches a deal. No cropping, no editing. */
export function LogoPicker({ remaining, busy = false, onFiles }: LogoPickerProps) {
  const inputId = useId();
  const [dragging, setDragging] = useState(false);
  const full = remaining <= 0;

  function drop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault();
    setDragging(false);
    if (busy || full) return;
    const files = pickLogoFiles(event.dataTransfer.files, remaining);
    if (files.length > 0) onFiles(files);
  }

  return (
    <div
      className={`logo-picker${dragging ? " dragging" : ""}${full ? " full" : ""}`}
      onDragOver={(event) => {
        event.preventDefault();
        if (!busy && !full) setDragging(true);
      }}
      onDragLeave={() => setDragging(false)}
      onDrop={drop}
    >
      <label htmlFor={inputId} className="logo-picker-label">
        {full
          ? "This deal has its two logos; remove one to add another."
          : "Drop the sponsor's logo here, or choose a file"}
      </label>
      <input
        id={inputId}
        type="file"
        accept="image/png,image/jpeg"
        multiple={remaining > 1}
        disabled={busy || full}
        aria-label="Choose a logo file"
        onChange={(event) => {
          const files = pickLogoFiles(event.target.files, remaining);
          event.target.value = "";
          if (files.length > 0) onFiles(files);
        }}
      />
      <span className="field-hint">
        PNG or JPEG, up to 5 MB, up to two variants (for example light and dark). The logo as it appears in your
        overlay, not a photo of it.
      </span>
    </div>
  );
}
