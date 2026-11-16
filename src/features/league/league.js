/**
 * Physical module: src/features/league/league.js
 * Single League data source and renderers for Rangliste (#app-screen-score) and the
 * compact Home League preview. The ranking is a local preview with sample entries
 * (no multiplayer backend exists); it is always labeled as such and never presented
 * as verified live users. Achievements are derived from real stored data.
 */
(function (global) {
  'use strict';

  /* The League values previously shipped as static markup on the Rangliste screen. */
  const LEAGUE_PREVIEW = Object.freeze({
    isDemo: true,
    name: 'Gold League',
    tier: 'gold',
    title: 'Weekly Focus League',
    period: 'diese Woche',
    promotionZone: 5,
    user: Object.freeze({ rank: 12, name: 'Du', score: 684 }),
    entries: Object.freeze([
      Object.freeze({ rank: 1, name: 'AnnaK', score: 926 }),
      Object.freeze({ rank: 2, name: 'Max_Müller', score: 890 }),
      Object.freeze({ rank: 3, name: 'StudyKing', score: 845 })
    ])
  });

  function daysLeftInWeek(nowTs) {
    const d = new Date(nowTs || Date.now());
    const weekday = (d.getDay() + 6) % 7;
    return 6 - weekday;
  }

  function getLeagueSnapshot() {
    const data = LEAGUE_PREVIEW;
    if (!data || !data.user || !Array.isArray(data.entries)) return null;
    const podium = data.entries.find(function (e) { return e.rank === 3; }) || null;
    const gapToPodium = podium && data.user.rank > 3 ? Math.max(0, podium.score - data.user.score) : 0;
    return {
      isDemo: data.isDemo,
      name: data.name,
      tier: data.tier,
      title: data.title,
      period: data.period,
      promotionZone: data.promotionZone,
      rank: data.user.rank,
      score: data.user.score,
      entries: data.entries.slice(),
      user: data.user,
      inPromotionZone: data.user.rank <= data.promotionZone,
      placesToPromotion: Math.max(0, data.user.rank - data.promotionZone),
      podiumScore: podium ? podium.score : null,
      gapToPodium: gapToPodium,
      podiumProgress: podium && podium.score > 0 ? Math.min(1, data.user.score / podium.score) : null,
      daysLeft: daysLeftInWeek()
    };
  }

  function el(tag, cls, text) {
    const node = document.createElement(tag);
    if (cls) node.className = cls;
    if (text !== undefined) node.textContent = text;
    return node;
  }
  function icon(name, cls) {
    const i = el('i', cls || '');
    i.setAttribute('data-lucide', name);
    i.setAttribute('aria-hidden', 'true');
    return i;
  }
  function initials(name) {
    const clean = String(name || '').replace(/[^A-Za-zÄÖÜäöüß0-9 _-]/g, '').trim();
    const parts = clean.split(/[\s_-]+/).filter(Boolean);
    if (!parts.length) return '?';
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
    return (parts[0][0] + parts[1][0]).toUpperCase();
  }
  function formatScore(n) {
    return Number(n).toLocaleString('de-DE');
  }
  function daysLabel(n) {
    if (n <= 0) return 'letzter Tag der Wochenwertung';
    return 'noch ' + n + (n === 1 ? ' Tag' : ' Tage') + ' Wochenwertung';
  }
  function refreshIcons() {
    if (global.lucide && typeof global.lucide.createIcons === 'function') {
      try { global.lucide.createIcons(); } catch (_) {}
    }
  }

  function leagueEmblem(snapshot, size) {
    const emblem = el('span', 'fm-league-emblem is-' + (snapshot ? snapshot.tier : 'none') + (size ? ' is-' + size : ''));
    emblem.setAttribute('aria-hidden', 'true');
    emblem.append(icon('trophy'));
    return emblem;
  }

  /* ---------- Achievements from real stored data ---------- */
  function computeAchievements() {
    const ins = global.FocusInsights ? global.FocusInsights.computeInsights() : null;
    let completedMissions = 0;
    try {
      if (typeof missionRepository !== 'undefined') {
        completedMissions = missionRepository.getAll().filter(function (m) { return m.status === 'completed'; }).length;
      }
    } catch (_) {}
    const total = ins ? ins.totalMinutes : 0;
    const bestStreak = ins ? ins.streak.best : 0;
    const fmt = global.FocusInsights ? global.FocusInsights.formatDuration : function (m) { return m + ' Min.'; };
    return [
      {
        id: 'mission-complete',
        icon: 'target',
        title: 'Mission Complete',
        copy: 'Erste Mission geschafft',
        unlocked: completedMissions > 0,
        progress: completedMissions > 0 ? null : 'Noch keine Mission abgeschlossen'
      },
      {
        id: 'rhythm',
        icon: 'flame',
        title: 'Im Rhythmus',
        copy: '3 Tage in Folge fokussiert',
        unlocked: bestStreak >= 3,
        progress: bestStreak >= 3 ? null : 'Beste Serie: ' + bestStreak + (bestStreak === 1 ? ' Tag' : ' Tage')
      },
      {
        id: 'ten-hours',
        icon: 'timer',
        title: '10 Stunden',
        copy: 'Zeit investiert',
        unlocked: total >= 600,
        progress: total >= 600 ? null : fmt(total) + ' von 10h'
      },
      {
        id: 'comeback',
        icon: 'rotate-ccw',
        title: 'Comeback',
        copy: 'Nach 7+ Tagen Pause zurückkehren',
        unlocked: Boolean(ins && ins.hasComeback),
        progress: ins && ins.hasComeback ? null : 'Noch nicht erreicht'
      }
    ];
  }

  /* ---------- Rangliste (#app-screen-score) ---------- */
  function renderLeagueHero(host, s) {
    host.replaceChildren();
    if (!s) {
      host.classList.add('is-unavailable');
      host.append(el('div', 'fm-league-unavailable', 'Ligadaten sind gerade nicht verfügbar.'));
      return;
    }
    host.classList.remove('is-unavailable');
    const top = el('div', 'fm-league-hero-top');
    top.append(leagueEmblem(s, 'lg'));
    const copy = el('div', 'fm-league-hero-copy');
    copy.append(el('span', 'fm-league-kicker', s.name.toUpperCase()));
    const rankRow = el('div', 'fm-league-rank-row');
    rankRow.append(el('span', 'fm-league-rank', '#' + s.rank));
    const scoreWrap = el('span', 'fm-league-score');
    scoreWrap.append(el('strong', 'tabular-nums', formatScore(s.score)), el('small', '', 'Focus Score · ' + s.period));
    rankRow.append(scoreWrap);
    copy.append(rankRow);
    top.append(copy);
    host.append(top);

    const zone = el('div', 'fm-league-zone');
    const zoneLabel = el('div', 'fm-league-zone-row');
    zoneLabel.append(el('span', '', 'Aufstiegszone: Top ' + s.promotionZone));
    zoneLabel.append(el('strong', s.inPromotionZone ? 'is-in' : '', s.inPromotionZone ? 'Du bist drin' : s.placesToPromotion + (s.placesToPromotion === 1 ? ' Platz entfernt' : ' Plätze entfernt')));
    zone.append(zoneLabel);
    if (s.podiumProgress !== null && s.gapToPodium > 0) {
      const track = el('div', 'fm-league-track');
      track.setAttribute('role', 'progressbar');
      track.setAttribute('aria-label', 'Fortschritt bis Platz 3');
      track.setAttribute('aria-valuemin', '0');
      track.setAttribute('aria-valuemax', String(s.podiumScore));
      track.setAttribute('aria-valuenow', String(s.score));
      const fill = el('i');
      fill.style.transform = 'scaleX(' + s.podiumProgress.toFixed(3) + ')';
      track.append(fill);
      zone.append(track);
      const meta = el('div', 'fm-league-zone-meta');
      meta.append(el('span', '', formatScore(s.gapToPodium) + ' Punkte bis Platz 3'), el('span', '', daysLabel(s.daysLeft)));
      zone.append(meta);
    } else {
      const meta = el('div', 'fm-league-zone-meta');
      meta.append(el('span', '', daysLabel(s.daysLeft)));
      zone.append(meta);
    }
    host.append(zone);
  }

  function rankingRow(entry, isUser) {
    const row = el('li', 'fm-rank-row' + (isUser ? ' is-user' : '') + (entry.rank <= 3 ? ' is-podium is-rank-' + entry.rank : ''));
    if (isUser) row.setAttribute('aria-current', 'true');
    row.append(el('span', 'fm-rank-pos tabular-nums', String(entry.rank)));
    const avatar = el('span', 'fm-rank-avatar', isUser ? '' : initials(entry.name));
    if (isUser) avatar.append(icon('user'));
    avatar.setAttribute('aria-hidden', 'true');
    row.append(avatar);
    const name = el('span', 'fm-rank-name', isUser ? 'Du' : entry.name);
    if (isUser) name.append(el('small', '', 'Dein Platz'));
    row.append(name);
    row.append(el('span', 'fm-rank-score tabular-nums', formatScore(entry.score)));
    return row;
  }

  function renderRanking(host, s) {
    host.replaceChildren();
    if (!s) return;
    const list = el('ol', 'fm-rank-list');
    list.setAttribute('aria-label', 'Rangliste ' + s.period);
    let lastRank = 0;
    s.entries.forEach(function (e) {
      list.append(rankingRow(e, false));
      lastRank = e.rank;
    });
    if (s.rank > lastRank + 1) {
      const gap = el('li', 'fm-rank-gap');
      gap.setAttribute('aria-hidden', 'true');
      gap.append(el('span'), el('span'), el('span'));
      list.append(gap);
    }
    list.append(rankingRow(s.user, true));
    host.append(list);
  }

  function renderAchievements(host) {
    host.replaceChildren();
    computeAchievements().forEach(function (a) {
      const tile = el('div', 'fm-achievement' + (a.unlocked ? ' is-unlocked' : ' is-locked'));
      const ic = el('span', 'fm-achievement-icon');
      ic.append(icon(a.icon));
      tile.append(ic);
      tile.append(el('strong', '', a.title));
      tile.append(el('span', 'fm-achievement-copy', a.copy));
      const state = el('span', 'fm-achievement-state');
      if (a.unlocked) {
        state.append(icon('check'), document.createTextNode('Erreicht'));
      } else {
        state.textContent = a.progress;
      }
      tile.append(state);
      host.append(tile);
    });
  }

  function renderLeagueScreen() {
    if (typeof document === 'undefined') return;
    const s = getLeagueSnapshot();
    const hero = document.getElementById('leagueHeroCard');
    if (hero) renderLeagueHero(hero, s);
    const ranking = document.getElementById('leagueRankingList');
    if (ranking) renderRanking(ranking, s);
    const demo = document.getElementById('leagueDemoNote');
    if (demo) demo.classList.toggle('hidden', !(s && s.isDemo));
    const ach = document.getElementById('leagueAchievements');
    if (ach) renderAchievements(ach);
    refreshIcons();
  }

  /* ---------- Home League preview ---------- */
  function renderHomeLeaguePreview() {
    if (typeof document === 'undefined') return;
    const card = document.getElementById('homeLeagueCard');
    if (!card) return;
    const s = getLeagueSnapshot();
    card.replaceChildren();
    if (!s) {
      card.classList.add('is-unavailable');
      card.append(leagueEmblem(null));
      const copy = el('span', 'home-league-copy');
      copy.append(el('strong', '', 'Liga nicht verfügbar'), el('small', '', 'Rangliste öffnen'));
      card.append(copy, icon('chevron-right', 'home-league-chevron'));
      card.setAttribute('aria-label', 'Liga nicht verfügbar. Rangliste öffnen');
      refreshIcons();
      return;
    }
    card.classList.remove('is-unavailable');
    card.append(leagueEmblem(s));
    const copy = el('span', 'home-league-copy');
    const title = el('span', 'home-league-title');
    title.append(el('strong', '', s.name));
    if (s.isDemo) title.append(el('span', 'fm-demo-chip', 'Vorschau'));
    copy.append(title);
    const meta = el('span', 'home-league-meta');
    meta.append(el('b', 'tabular-nums', formatScore(s.score)), document.createTextNode(' Focus Score'));
    if (s.gapToPodium > 0) meta.append(document.createTextNode(' · ' + formatScore(s.gapToPodium) + ' bis Platz 3'));
    copy.append(meta);
    if (s.podiumProgress !== null && s.gapToPodium > 0) {
      const track = el('span', 'home-league-track');
      track.setAttribute('aria-hidden', 'true');
      const fill = el('i');
      fill.style.transform = 'scaleX(' + s.podiumProgress.toFixed(3) + ')';
      track.append(fill);
      copy.append(track);
    }
    card.append(copy);
    const rank = el('span', 'home-league-rank');
    rank.append(el('strong', 'tabular-nums', '#' + s.rank), el('small', '', 'Platz'));
    card.append(rank, icon('chevron-right', 'home-league-chevron'));
    card.setAttribute('aria-label', s.name + (s.isDemo ? ' (Vorschau)' : '') + ', Platz ' + s.rank + ', ' + s.score + ' Focus Score. Rangliste ansehen');
    refreshIcons();
  }

  function openLeagueFromHome() {
    if (typeof global.navigateForward === 'function') {
      global.navigateForward('score', { parent: 'home', rootSection: 'home' });
    } else if (typeof global.setAppScreen === 'function') {
      global.setAppScreen('score');
    }
  }

  const previousApply = global.applyAppScreenImmediately;
  if (typeof previousApply === 'function') {
    global.applyAppScreenImmediately = function (screenName) {
      if (screenName === 'score') renderLeagueScreen();
      return previousApply.apply(this, arguments);
    };
  }

  if (typeof document !== 'undefined') {
    document.addEventListener('DOMContentLoaded', function () {
      renderHomeLeaguePreview();
      renderLeagueScreen();
    });
  }

  const League = {
    getSnapshot: getLeagueSnapshot,
    computeAchievements: computeAchievements,
    renderScreen: renderLeagueScreen,
    renderHomePreview: renderHomeLeaguePreview
  };
  global.FocusLeague = League;
  global.openLeagueFromHome = openLeagueFromHome;
  if (global.FocusModeApp && global.FocusModeApp.features) {
    global.FocusModeApp.features.league = League;
  }
})(window);
