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

  welcome_title: 'Tu música, en números',
  welcome_subtitle: 'Tus canciones y artistas top, tu historial de escuchas y un ranking con tus amigos.',
  welcome_privacy: 'Todo se calcula y se guarda en este dispositivo. No se sube nada salvo que tú lo decidas.',
  welcome_sign_in: 'Entrar con Spotify',
  welcome_sign_in_soon: 'El acceso con Spotify llega en la próxima versión.',
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
