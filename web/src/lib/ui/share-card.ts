/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/** Everything the card shows, already translated and formatted. */
export interface ShareCardData {
  readonly brand: string
  readonly period: string
  readonly minutes: string
  readonly minutesLabel: string
  readonly streamsLine: string
  readonly topArtistsTitle: string
  readonly topArtists: readonly { name: string; imageUrl: string | null }[]
  readonly topTracksTitle: string
  readonly topTracks: readonly string[]
  readonly footer: string
}

const WIDTH = 1080
const HEIGHT = 1920
const IMAGE_TIMEOUT_MS = 5_000
const DISPLAY = '"Space Grotesk", system-ui, -apple-system, sans-serif'
const BODY = 'system-ui, -apple-system, "Segoe UI", sans-serif'

/**
 * Loads a cover for the canvas. It is requested with CORS: if Spotify's CDN does not allow it, the
 * image still loads but taints the canvas, and exporting fails (handled by `createShareImage`).
 */
function loadImage(url: string): Promise<HTMLImageElement | null> {
  return new Promise((resolve) => {
    const image = new Image()
    image.crossOrigin = 'anonymous'
    image.referrerPolicy = 'no-referrer'
    const timer = setTimeout(() => resolve(null), IMAGE_TIMEOUT_MS)
    image.onload = () => {
      clearTimeout(timer)
      resolve(image)
    }
    image.onerror = () => {
      clearTimeout(timer)
      resolve(null)
    }
    image.src = url
  })
}

/** Shortens text with an ellipsis so it fits `maxWidth`. */
function fit(context: CanvasRenderingContext2D, text: string, maxWidth: number): string {
  if (context.measureText(text).width <= maxWidth) return text
  let end = text.length
  while (end > 1 && context.measureText(`${text.slice(0, end)}…`).width > maxWidth) end--
  return `${text.slice(0, end)}…`
}

/** Draws the 1080 × 1920 story card and exports it as PNG. Throws if the canvas is tainted. */
export async function renderShareCard(
  data: ShareCardData,
  withImages: boolean,
  doc: Document = document,
): Promise<Blob> {
  await doc.fonts?.load(`700 64px ${DISPLAY}`).catch(() => undefined)
  const canvas = doc.createElement('canvas')
  canvas.width = WIDTH
  canvas.height = HEIGHT
  const context = canvas.getContext('2d')
  if (!context) throw new Error('Canvas is not available')

  // Background: the brand gradient over ink, like the app's story cards.
  context.fillStyle = '#0a0a0f'
  context.fillRect(0, 0, WIDTH, HEIGHT)
  const gradient = context.createLinearGradient(0, 0, WIDTH, HEIGHT)
  gradient.addColorStop(0, 'rgba(200, 255, 77, 0.55)')
  gradient.addColorStop(0.5, 'rgba(77, 232, 255, 0.35)')
  gradient.addColorStop(1, 'rgba(139, 124, 255, 0.6)')
  context.fillStyle = gradient
  context.fillRect(0, 0, WIDTH, HEIGHT)
  context.fillStyle = 'rgba(10, 10, 15, 0.55)'
  context.fillRect(0, 0, WIDTH, HEIGHT)

  const margin = 96
  const text = (value: string, y: number, font: string, color = '#f4f4f8') => {
    context.font = font
    context.fillStyle = color
    context.fillText(fit(context, value, WIDTH - 2 * margin), margin, y)
  }

  text(data.brand, 160, `700 44px ${DISPLAY}`, '#c8ff4d')
  text(data.period, 250, `700 72px ${DISPLAY}`)
  text(data.minutes, 470, `700 200px ${DISPLAY}`)
  text(data.minutesLabel, 550, `500 48px ${BODY}`, '#a3a3b8')
  text(data.streamsLine, 630, `500 40px ${BODY}`)

  text(data.topArtistsTitle, 780, `700 52px ${DISPLAY}`, '#c8ff4d')
  const images = withImages
    ? await Promise.all(data.topArtists.map((artist) => (artist.imageUrl ? loadImage(artist.imageUrl) : null)))
    : []
  data.topArtists.slice(0, 5).forEach((artist, index) => {
    const y = 840 + index * 120
    const image = images[index]
    context.save()
    context.beginPath()
    context.arc(margin + 48, y + 48, 48, 0, Math.PI * 2)
    context.closePath()
    context.clip()
    if (image) {
      context.drawImage(image, margin, y, 96, 96)
    } else {
      context.fillStyle = 'rgba(244, 244, 248, 0.15)'
      context.fillRect(margin, y, 96, 96)
    }
    context.restore()
    context.font = `600 44px ${BODY}`
    context.fillStyle = '#f4f4f8'
    context.fillText(fit(context, `${index + 1}. ${artist.name}`, WIDTH - 2 * margin - 130), margin + 130, y + 62)
  })

  const tracksTop = 840 + Math.min(5, data.topArtists.length) * 120 + 80
  text(data.topTracksTitle, tracksTop, `700 52px ${DISPLAY}`, '#c8ff4d')
  data.topTracks.slice(0, 5).forEach((track, index) => {
    text(`${index + 1}. ${track}`, tracksTop + 80 + index * 68, `500 42px ${BODY}`)
  })

  text(data.footer, HEIGHT - 96, `500 34px ${BODY}`, '#a3a3b8')

  return new Promise((resolve, reject) => {
    try {
      // A tainted canvas (covers without CORS) throws a SecurityError here.
      canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('Empty image'))), 'image/png')
    } catch (error) {
      reject(error)
    }
  })
}

/** With covers if the CDN allows it, otherwise the same card without them. */
export async function createShareImage(
  data: ShareCardData,
  render: (data: ShareCardData, withImages: boolean) => Promise<Blob> = renderShareCard,
): Promise<Blob> {
  try {
    return await render(data, true)
  } catch {
    return render(data, false)
  }
}

export type ShareOutcome = 'shared' | 'downloaded' | 'cancelled'

/** iOS share sheet when files can be shared (Safari 15+), a download otherwise. */
export async function shareOrDownload(
  blob: Blob,
  filename: string,
  deps: { navigator: Navigator; document: Document } = { navigator, document },
): Promise<ShareOutcome> {
  const file = new File([blob], filename, { type: 'image/png' })
  if (deps.navigator.canShare?.({ files: [file] })) {
    try {
      await deps.navigator.share({ files: [file] })
      return 'shared'
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return 'cancelled'
      // Share sheet unavailable after all: fall back to a download.
    }
  }
  const url = URL.createObjectURL(blob)
  const link = deps.document.createElement('a')
  link.href = url
  link.download = filename
  deps.document.body.append(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
  return 'downloaded'
}
