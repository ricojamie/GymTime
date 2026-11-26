import { useEffect, useLayoutEffect, useRef } from 'react'

// Capture only the two neighbors the user moved. Editing, filtering and scrolling do no layout reads.
export function useAdjacentReorder() {
  const pending = useRef<{ nodes: { element: HTMLElement; rect: DOMRect }[]; focused: HTMLButtonElement | null } | null>(null)
  const animations = useRef<Animation[]>([])

  useEffect(() => {
    const cancel = () => {
      animations.current.forEach(animation => animation.cancel())
      animations.current = []
    }
    // The theme combines the OS preference and the user's local setting on the root.
    const root = document.documentElement
    const observer = new MutationObserver(() => { if (root.dataset.motion !== 'full') cancel() })
    observer.observe(root, { attributes: true, attributeFilter: ['data-motion'] })
    return () => { observer.disconnect(); cancel() }
  }, [])

  function capture(button: HTMLButtonElement, direction: number) {
    const item = button.closest<HTMLElement>('[data-order-item]')
    const neighbor = direction < 0 ? item?.previousElementSibling : item?.nextElementSibling
    if (!item || !(neighbor instanceof HTMLElement) || !neighbor.hasAttribute('data-order-item')) return
    const canMove = typeof item.animate === 'function' && window.matchMedia?.('(prefers-reduced-motion: no-preference)').matches
      && document.documentElement.dataset.motion === 'full'
    pending.current = {
      nodes: canMove ? [item, neighbor].map(element => ({ element, rect: element.getBoundingClientRect() })) : [],
      focused: button === document.activeElement ? button : null,
    }
  }

  useLayoutEffect(() => {
    const change = pending.current
    if (!change) return
    pending.current = null
    const { focused } = change
    if (focused && (document.activeElement === document.body || document.activeElement === focused)) {
      const target = focused.disabled ? focused.parentElement?.querySelector<HTMLButtonElement>('button:enabled') : focused
      if (target && target !== document.activeElement) target.focus({ preventScroll: true })
    }
    animations.current.forEach(animation => animation.cancel())
    animations.current = []
    const moves = change.nodes.map(({ element, rect }) => {
      const next = element.getBoundingClientRect()
      return { element, x: rect.left - next.left, y: rect.top - next.top }
    })
    for (const { element, x, y } of moves) {
      if (Math.abs(x) + Math.abs(y) < 1) continue
      const clamp = (distance: number) => Math.max(-240, Math.min(240, distance))
      animations.current.push(element.animate(
        [{ translate: `${clamp(x)}px ${clamp(y)}px` }, { translate: '0 0' }],
        { duration: 220, easing: 'cubic-bezier(.18,.74,.22,1)' },
      ))
    }
  })

  return capture
}