/*
 * MusicMania - open-source Spotify listening statistics for the web.
 * Copyright (C) 2026 MusicMania contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import type { Messages } from './en'

/** Spanish strings. Typed as Messages, so a missing or extra key does not compile. */
export const es: Messages = {
  app_name: 'MusicMania',
  common_back: 'Atrás',

  nav_home: 'Inicio',
  nav_history: 'Historial',
  nav_settings: 'Ajustes',
  nav_label: 'Principal',

  welcome_not_configured: 'El acceso no está configurado en esta versión.',
  welcome_session_expired: 'Tu sesión ha caducado. Vuelve a entrar para seguir sincronizando.',
  welcome_sign_in_failed: 'No se pudo entrar. Inténtalo de nuevo.',

  callback_signing_in: 'Entrando…',
  callback_failed_title: 'El acceso no se completó',
  callback_failed_message:
    'Puede que el enlace haya caducado o se abriera en otro navegador. Vuelve a empezar desde la app.',
  callback_back: 'Volver a MusicMania',

  home_greeting: 'Hola, {name}',
  home_greeting_plain: 'Hola',
  home_today: 'Hoy',
  home_this_week: 'Esta semana',
  home_minutes: 'min',
  home_streams_one: '{count} stream',
  home_streams_other: '{count} streams',
  home_streak: 'Racha',
  home_days_unit_one: 'día',
  home_days_unit_other: 'días',
  home_streak_best_one: 'Mejor: {count} día',
  home_streak_best_other: 'Mejor: {count} días',
  home_top_artist: 'Artista top de la semana',
  home_now_playing: 'Sonando ahora',
  home_paused: 'En pausa',
  home_empty_title: 'Tu historial empieza ahora',
  home_empty_message:
    'Spotify comparte tus últimas 50 escuchas: se guardan cada vez que abres MusicMania. Importa tus datos de Spotify para ver todo tu historial.',
  home_synced: 'Sincronizado {time}',
  home_never_synced: 'Aún sin sincronizar',
  home_syncing: 'Sincronizando…',
  home_sync_now: 'Sincronizar',
  home_gap_warning:
    'Has escuchado más de 50 canciones desde la última sincronización, así que puede faltar alguna. Abre la app más a menudo o importa tus datos de Spotify para completar el hueco.',
  home_open_daily: 'Abre MusicMania cada día: Spotify solo guarda tus últimas 50 escuchas.',

  history_title: 'Historial',
  history_empty: 'Aún no hay escuchas. Aparecerán aquí tras la primera sincronización.',
  history_load_more: 'Cargar más',
  history_today: 'Hoy',
  history_yesterday: 'Ayer',

  error_network: 'Sin conexión. Tus datos siguen aquí; se sincronizará cuando vuelvas a tener red.',
  error_forbidden:
    'Spotify ha rechazado esta cuenta: hay que añadirla a la app MusicMania en el dashboard de Spotify (máximo 5 usuarios).',
  error_rate_limited: 'Spotify nos pide ir más despacio. Inténtalo en unos minutos.',
  error_generic: 'Algo falló al hablar con Spotify. Inténtalo más tarde.',

  settings_account: 'Cuenta',
  settings_signed_in_as: 'Sesión iniciada como {name}',
  settings_sign_out: 'Cerrar sesión',
  settings_sign_out_confirm: '¿Cerrar sesión y borrar todos los datos de MusicMania de este dispositivo?',

  welcome_title: 'Tu música, en números',
  welcome_subtitle: 'Tus canciones y artistas top, tu historial de escuchas y un ranking con tus amigos.',
  welcome_privacy: 'Todo se calcula y se guarda en este dispositivo. No se sube nada salvo que tú lo decidas.',
  welcome_sign_in: 'Entrar con Spotify',
  welcome_install_title: 'Instálala en tu iPhone',
  welcome_install_message: 'Añade MusicMania a tu pantalla de inicio para usarla como una app.',
  welcome_install_action: 'Cómo instalarla',
  welcome_settings: 'Ajustes',

  install_title: 'Instalar MusicMania',
  install_subtitle: 'Sin App Store: funciona como una app desde tu pantalla de inicio.',
  install_ios_title: 'iPhone y iPad (Safari)',
  install_ios_step_1: 'Abre esta página en Safari.',
  install_ios_step_2: 'Toca el botón Compartir (el cuadrado con una flecha).',
  install_ios_step_3: 'Elige «Añadir a pantalla de inicio» y toca «Añadir».',
  install_ios_step_4: 'Abre MusicMania desde tu pantalla de inicio.',
  install_android_title: 'Android (Chrome)',
  install_android_step_1: 'Abre el menú (tres puntos).',
  install_android_step_2: 'Toca «Instalar aplicación» o «Añadir a pantalla de inicio».',
  install_why_title: '¿Por qué instalarla?',
  install_why_message:
    'Instalada, MusicMania se abre a pantalla completa y tus datos se quedan en tu dispositivo. Ábrela cada día: Spotify solo comparte tus últimas 50 escuchas, así que la app las guarda cada vez que la abres.',
  install_already: 'Ya estás usando la app instalada.',

  settings_title: 'Ajustes',
  settings_appearance: 'Apariencia',
  settings_theme: 'Tema',
  settings_theme_system: 'Sistema',
  settings_theme_light: 'Claro',
  settings_theme_dark: 'Oscuro',
  settings_language: 'Idioma',
  settings_language_system: 'Sistema',
  settings_language_en: 'English',
  settings_language_es: 'Español',
  settings_motion: 'Animaciones',
  settings_motion_system: 'Sistema',
  settings_motion_reduce: 'Reducidas',
  settings_motion_full: 'Completas',
  settings_about: 'Acerca de',
  settings_version: 'Versión {version}',
  settings_source: 'Código fuente (GPL-3.0)',
}
