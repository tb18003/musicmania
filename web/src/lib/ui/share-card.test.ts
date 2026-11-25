/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { describe, expect, it, vi } from 'vitest'
import { createShareImage, shareOrDownload, type ShareCardData } from './share-card'

const DATA: ShareCardData = {
  brand: 'MusicMania',
  period: 'This year',
  minutes: '12,345',
  minutesLabel: 'minutes of music',
  streamsLine: '3,000 streams across 900 different songs.',
  topArtistsTitle: 'Top artists',
  topArtists: [{ name: 'Coral Echo', imageUrl: 'https://i.scdn.co/image/a' }],
  topTracksTitle: 'Top songs',
  topTracks: ['Low Tide · Coral Echo'],
  footer: 'MusicMania',
}
const PNG = new Blob(['png'], { type: 'image/png' })

describe('createShareImage', () => {
  it('uses the covers when the CDN allows exporting the canvas', async () => {
    const render = vi.fn(async () => PNG)
    expect(await createShareImage(DATA, render)).toBe(PNG)
    expect(render).toHaveBeenCalledWith(DATA, true)
    expect(render).toHaveBeenCalledOnce()
  })

  it('falls back to a card without covers when the canvas is tainted (no CORS)', async () => {
    const render = vi.fn(async (_: ShareCardData, withImages: boolean) => {
      if (withImages) throw new DOMException('The operation is insecure.', 'SecurityError')
      return PNG
    })
    expect(await createShareImage(DATA, render)).toBe(PNG)
    expect(render.mock.calls.map((call) => call[1])).toEqual([true, false])
  })
})

describe('shareOrDownload', () => {
  it('opens the share sheet when files can be shared', async () => {
    const share = vi.fn(async (data: { files: File[] }) => void data)
    const nav = { canShare: () => true, share } as unknown as Navigator
    expect(await shareOrDownload(PNG, 'recap.png', { navigator: nav, document })).toBe('shared')
    const file = share.mock.calls[0]?.[0].files[0]
    expect(file?.name).toBe('recap.png')
    expect(file?.type).toBe('image/png')
  })

  it('closing the share sheet is not an error, and downloads nothing', async () => {
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click')
    const nav = {
      canShare: () => true,
      share: async () => {
        throw new DOMException('Share canceled', 'AbortError')
      },
    } as unknown as Navigator
    expect(await shareOrDownload(PNG, 'recap.png', { navigator: nav, document })).toBe('cancelled')
    expect(click).not.toHaveBeenCalled()
  })

  it('without the share sheet, downloads the PNG', async () => {
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
    URL.createObjectURL = vi.fn(() => 'blob:test')
    URL.revokeObjectURL = vi.fn()
    expect(await shareOrDownload(PNG, 'recap.png', { navigator: {} as Navigator, document })).toBe('downloaded')
    expect(click).toHaveBeenCalledOnce()
  })
})
