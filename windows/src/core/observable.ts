/** Minimal reactive value: current value + change subscription (synchronous notifications, only on real change). */
export interface Observable<T> {
  readonly value: T
  subscribe(cb: (v: T) => void): () => void
}

export class MutableObservable<T> implements Observable<T> {
  private listeners = new Set<(v: T) => void>()
  constructor(private current: T) {}

  get value(): T {
    return this.current
  }

  set value(v: T) {
    if (Object.is(v, this.current)) return
    this.current = v
    for (const l of [...this.listeners]) l(v)
  }

  subscribe(cb: (v: T) => void): () => void {
    this.listeners.add(cb)
    return () => this.listeners.delete(cb)
  }
}
