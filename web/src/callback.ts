/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

// The web sign-in redirect (/musicmania/auth/callback): finishes PKCE and goes back to the app.
import { createAuth } from './app/container'
import { MusicManiaDatabase } from './lib/data/db/database'
import type { MessageKey } from './lib/ui/i18n/en'
import { detectLocale, translate } from './lib/ui/i18n/translate'
import './lib/ui/tokens.css'

const callbackUrl = window.location.href
// Take the authorization code out of the address bar and history before anything else runs.
window.history.replaceState(null, '', window.location.pathname)

const appUrl = new URL('../', window.location.href)
const locale = detectLocale(navigator.languages)
const t = (key: MessageKey) => translate(locale, key)
document.documentElement.lang = locale

const main = document.getElementById('callback')
function show(...nodes: Node[]) {
  main?.replaceChildren(...nodes)
}
function element<K extends keyof HTMLElementTagNameMap>(tag: K, text: string): HTMLElementTagNameMap[K] {
  const node = document.createElement(tag)
  node.textContent = text
  return node
}

show(element('p', t('callback_signing_in')))

const result = await createAuth(new MusicManiaDatabase()).completeLogin(callbackUrl)
if (result.ok) {
  window.location.replace(appUrl)
} else {
  const back = element('a', t('callback_back'))
  back.href = appUrl.toString()
  show(element('h1', t('callback_failed_title')), element('p', t('callback_failed_message')), back)
}
