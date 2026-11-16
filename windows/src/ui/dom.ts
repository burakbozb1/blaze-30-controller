type Child = Node | string | null | undefined | false

/** Tiny element builder: h('div', { class: 'x', testid: 'y', onclick: fn }, child, 'text'). */
export function h<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  props: Record<string, unknown> = {},
  ...children: Child[]
): HTMLElementTagNameMap[K] {
  const el = document.createElement(tag)
  for (const [k, v] of Object.entries(props)) {
    if (v === undefined || v === null || v === false) continue
    if (k === 'class') el.className = String(v)
    else if (k === 'testid') el.setAttribute('data-testid', String(v))
    else if (k === 'style' && typeof v === 'object') Object.assign(el.style, v)
    else if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2), v as EventListener)
    else el.setAttribute(k, v === true ? '' : String(v))
  }
  for (const c of children) if (c) el.append(c)
  return el
}

export function svg(markup: string): SVGElement {
  const t = document.createElement('template')
  t.innerHTML = markup.trim()
  return t.content.firstElementChild as SVGElement
}

export const POWER_ICON =
  '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><path d="M12 3.2v8.6"/><path d="M6.6 6.9a8.2 8.2 0 1 0 10.8 0"/></svg>'
