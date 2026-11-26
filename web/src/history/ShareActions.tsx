import { useEffect, useMemo, useState } from 'react'
import { Copy, ImageDown } from 'lucide-react'
import { formatWorkoutShare, renderWorkoutPng, type buildWorkoutShare } from '../domain/workoutShare'
import type { Preferences } from '../db/types'
import { iconUrls } from '../theme'

type Share = NonNullable<ReturnType<typeof buildWorkoutShare>>

export function ShareActions({ share, prefs }: { share: Share; prefs: Preferences }) {
  const [file, setFile] = useState<File | null>(null)
  const [status, setStatus] = useState('')
  const text = useMemo(() => formatWorkoutShare(share), [share])
  useEffect(() => {
    let cancelled = false
    setFile(null)
    const accent = getComputedStyle(document.documentElement).getPropertyValue('--accent').trim()
    renderWorkoutPng(share, {accent, unit:prefs.preferredWeightUnit, iconUrl:iconUrls(prefs.appIcon).stamp}).then(blob => {
      if (!cancelled) setFile(new File([blob], 'ironlog-workout-' + share.date + '.png', { type:'image/png' }))
    }).catch(() => { if (!cancelled) setStatus('Couldn’t create a workout image.') })
    return () => { cancelled = true }
  }, [share, prefs.preferredWeightUnit, prefs.appIcon, prefs.themeColor, prefs.customThemeColor])

  async function sharePng() {
    if (!file) return
    if (navigator.canShare?.({files:[file]})) {
      try {
        await navigator.share({files:[file], title:share.title})
      } catch (err) {
        if (err instanceof DOMException && err.name === 'AbortError') return
        setStatus('Sharing wasn’t available. The image was downloaded.')
        download()
      }
    } else {
      download()
      setStatus('Workout image downloaded.')
    }
    function download() {
      const link = document.createElement('a')
      const url = URL.createObjectURL(file!)
      link.href = url
      link.download = file!.name
      document.body.append(link)
      link.click()
      link.remove()
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000)
    }
  }

  return <>
    <div className="his-share">
      <button className="btn" type="button" disabled={!file} onClick={() => void sharePng()}><ImageDown size={18}/>Share PNG</button>
      <button className="btn" type="button" onClick={async () => {
        try { await navigator.clipboard.writeText(text); setStatus('Workout text copied.') }
        catch { setStatus('Select and copy the workout text below.') }
      }}><Copy size={18}/>Copy text</button>
    </div>
    {status && <p role="status" className="his-message">{status}</p>}
    {status.startsWith('Select and copy') && <textarea aria-label="Workout share text" className="his-input" rows={5} readOnly value={text} onFocus={e => e.target.select()}/>}
  </>
}
