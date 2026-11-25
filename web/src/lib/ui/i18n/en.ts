/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/** English strings: the source of truth for the keys. Placeholders use {name}. */
export const en = {
  app_name: 'MusicMania',
  common_back: 'Back',

  welcome_title: 'Your music, in numbers',
  welcome_subtitle: 'Top tracks and artists, listening history and a ranking with your friends.',
  welcome_privacy: 'Everything is computed and stored on this device. Nothing is uploaded unless you choose to.',
  welcome_sign_in: 'Sign in with Spotify',
  welcome_sign_in_soon: 'Spotify sign-in arrives in the next version.',
  welcome_install_title: 'Install it on your iPhone',
  welcome_install_message: 'Add MusicMania to your home screen to use it like an app.',
  welcome_install_action: 'How to install',
  welcome_settings: 'Settings',

  install_title: 'Install MusicMania',
  install_subtitle: 'No App Store needed: it works like an app from your home screen.',
  install_ios_title: 'iPhone and iPad (Safari)',
  install_ios_step_1: 'Open this page in Safari.',
  install_ios_step_2: 'Tap the Share button (the square with an arrow).',
  install_ios_step_3: 'Choose "Add to Home Screen" and tap "Add".',
  install_ios_step_4: 'Open MusicMania from your home screen.',
  install_android_title: 'Android (Chrome)',
  install_android_step_1: 'Open the menu (three dots).',
  install_android_step_2: 'Tap "Install app" or "Add to Home screen".',
  install_why_title: 'Why install it?',
  install_why_message:
    'Installed, MusicMania opens full screen and your data stays on your device. Open it every day: Spotify only shares your last 50 plays, so the app saves them each time you open it.',
  install_already: 'You are already using the installed app.',

  settings_title: 'Settings',
  settings_appearance: 'Appearance',
  settings_theme: 'Theme',
  settings_theme_system: 'System',
  settings_theme_light: 'Light',
  settings_theme_dark: 'Dark',
  settings_language: 'Language',
  settings_language_system: 'System',
  settings_language_en: 'English',
  settings_language_es: 'Español',
  settings_motion: 'Animations',
  settings_motion_system: 'System',
  settings_motion_reduce: 'Reduced',
  settings_motion_full: 'Full',
  settings_about: 'About',
  settings_version: 'Version {version}',
  settings_source: 'Source code (GPL-3.0)',
} as const

export type MessageKey = keyof typeof en
export type Messages = Record<MessageKey, string>
