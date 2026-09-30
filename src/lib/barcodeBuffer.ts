/** USB scanners type quickly and finish with Enter. Slow human typing is ignored. */
export function createBarcodeWedge(onScan: (code: string) => void, gapMs = 50) {
  let buffer = '';
  let last = 0;
  return function onKey(event: KeyboardEvent) {
    const now = Date.now();
    if (now - last > gapMs) buffer = '';
    last = now;
    if (event.key === 'Enter') {
      if (buffer.length >= 4) {
        const code = buffer;
        buffer = '';
        event.preventDefault();
        event.stopPropagation();
        onScan(code);
      }
      return;
    }
    if (event.key.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey) {
      buffer += event.key;
    }
  };
}
