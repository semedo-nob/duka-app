export function money(n: number): string {
  return 'KSh ' + n.toLocaleString('en-KE', { maximumFractionDigits: 0 });
}
