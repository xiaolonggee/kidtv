'use strict';
/* 宝宝动画屋 · 管理后台 SPA（无构建依赖的原生 JS） */

const $ = s => document.querySelector(s);
const TOKEN_KEY = 'kidtv.admin.token';
let token = localStorage.getItem(TOKEN_KEY) || '';

function toast(msg) {
  const t = $('#toast'); t.textContent = msg; t.classList.add('show');
  clearTimeout(toast._t); toast._t = setTimeout(() => t.classList.remove('show'), 2600);
}
const esc = s => String(s ?? '').replace(/[&<>"']/g, c =>
  ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const fmtSize = n => n > 1e9 ? (n / 1e9).toFixed(2) + ' GB' : n > 1e6 ? (n / 1e6).toFixed(1) + ' MB' : Math.round(n / 1e3) + ' KB';

async function api(path, opts = {}) {
  const headers = Object.assign({ Authorization: 'Bearer ' + token }, opts.headers || {});
  if (opts.json !== undefined) {
    headers['Content-Type'] = 'application/json';
    opts.body = JSON.stringify(opts.json);
  }
  const res = await fetch(path, Object.assign({}, opts, { headers }));
  if (res.status === 401) { showLogin(); throw new Error('未登录'); }
  if (!res.ok) {
    let msg = res.statusText;
    try { msg = (await res.json()).detail || msg; } catch (e) {}
    throw new Error(msg);
  }
  return res.headers.get('content-type')?.includes('json') ? res.json() : res;
}

/* ===================== 登录 ===================== */
function showLogin() {
  token = ''; localStorage.removeItem(TOKEN_KEY);
  $('#loginView').classList.remove('hidden');
  $('#appView').classList.add('hidden');
}
async function doLogin() {
  const pw = $('#pw').value;
  try {
    const res = await fetch('/api/admin/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ password: pw }),
    });
    if (!res.ok) throw new Error((await res.json()).detail || '登录失败');
    token = (await res.json()).token;
    localStorage.setItem(TOKEN_KEY, token);
    $('#loginErr').classList.add('hidden');
    boot();
  } catch (e) {
    const el = $('#loginErr'); el.textContent = e.message; el.classList.remove('hidden');
  }
}
$('#loginBtn').addEventListener('click', doLogin);
$('#pw').addEventListener('keydown', e => { if (e.key === 'Enter') doLogin(); });
$('#logoutBtn').addEventListener('click', showLogin);

/* ===================== 视图切换 ===================== */
document.querySelectorAll('.tab').forEach(t => t.addEventListener('click', () => {
  document.querySelectorAll('.tab').forEach(x => x.classList.toggle('on', x === t));
  document.querySelectorAll('.view').forEach(v => v.classList.add('hidden'));
  $('#view-' + t.dataset.view).classList.remove('hidden');
  if (t.dataset.view === 'dash') loadDash();
  if (t.dataset.view === 'devices') loadDevices();
  if (t.dataset.view === 'content') loadContent();
}));

/* ===================== 概览 ===================== */
let dashMonth = null;  // 'YYYY-MM'
$('#dashDevice').addEventListener('change', () => { dashMonth = null; loadStats(); });
$('#calPrev').addEventListener('click', () => moveMonth(-1));
$('#calNext').addEventListener('click', () => moveMonth(1));
function moveMonth(d) {
  let [y, m] = (dashMonth || new Date().toISOString().slice(0, 7)).split('-').map(Number);
  m += d; if (m < 1) { m = 12; y--; } if (m > 12) { m = 1; y++; }
  dashMonth = `${y}-${String(m).padStart(2, '0')}`;
  loadStats();
}

async function loadDash() {
  const { devices } = await api('/api/admin/devices');
  const sel = $('#dashDevice');
  const prev = sel.value;
  sel.innerHTML = devices.map(d => `<option value="${d.id}">${esc(d.name)} (${d.id})</option>`).join('');
  if (devices.some(d => d.id === prev)) sel.value = prev;
  $('#dashEmpty').classList.toggle('hidden', devices.length > 0);
  $('#dashBody').classList.toggle('hidden', devices.length === 0);
  if (devices.length) loadStats();
}

async function loadStats() {
  const id = $('#dashDevice').value; if (!id) return;
  const q = dashMonth ? `&month=${dashMonth}` : '';
  const st = await api(`/api/admin/stats?device_id=${id}${q}`);
  dashMonth = st.month;
  $('#stToday').innerHTML = `${st.today.eps} <small>集 · ${Math.round(st.today.sec / 60)} 分钟</small>`;
  $('#stStreak').innerHTML = `${st.streak} <small>天</small>`;
  const weekSec = st.week.reduce((a, d) => a + d.sec, 0);
  $('#stWeek').innerHTML = `${Math.round(weekSec / 60)} <small>分钟</small>`;

  // 日历
  const [y, m] = st.month.split('-').map(Number);
  const days = new Date(y, m, 0).getDate();
  const lead = (new Date(y, m - 1, 1).getDay() + 6) % 7;
  const today = st.today.date;
  const STICKERS = ['🌟', '🐰', '🍓', '🎈', '🦄', '🌈', '🐳', '🌻'];
  $('#calTitle').textContent = `${y}年${m}月`;
  let html = '<div class="day blank"></div>'.repeat(lead);
  for (let d = 1; d <= days; d++) {
    const ds = `${st.month}-${String(d).padStart(2, '0')}`;
    const h = st.calendar[ds];
    html += `<div class="day ${h ? 'on' : ''} ${ds === today ? 'today' : ''}" title="${ds}${h ? ` · ${h.eps}集 ${Math.round(h.sec / 60)}分钟` : ''}">
      <span>${d}</span>${h ? `<span class="stk">${STICKERS[d % STICKERS.length]}</span><span class="eps">${h.eps}集</span>` : ''}</div>`;
  }
  $('#calDays').innerHTML = html;

  // 本周柱状
  const maxE = Math.max(1, ...st.week.map(d => d.eps));
  $('#weekBars').innerHTML = st.week.map(d => `
    <div class="wb" title="${d.date} · ${d.eps}集 ${Math.round(d.sec / 60)}分钟">
      <i style="height:${d.eps / maxE * 100}%"></i>
      <span class="lbl">${'一二三四五六日'[(new Date(d.date).getDay() + 6) % 7]}<br>${d.eps}集</span>
    </div>`).join('');

  // 系列 / 单集
  const maxP = Math.max(1, ...st.series.map(s => s.plays));
  $('#seriesStats').innerHTML = st.series.length ? `<table class="tbl">
    <tr><th>语言</th><th>系列</th><th>次数</th><th>时长</th><th></th></tr>
    ${st.series.sort((a, b) => b.plays - a.plays).map(s => `<tr>
      <td>${esc(s.language)}</td><td>${esc(s.series)}</td><td><b>${s.plays}</b></td>
      <td>${Math.round(s.sec / 60)} 分钟</td>
      <td><div class="bar-mini"><i style="width:${s.plays / maxP * 100}%"></i></div></td></tr>`).join('')}
  </table>` : '<p class="muted">还没有观看记录</p>';
  $('#episodeStats').innerHTML = st.episodes.length ? `<table class="tbl">
    <tr><th>系列</th><th>集</th><th>标题</th><th>次数</th></tr>
    ${st.episodes.sort((a, b) => b.plays - a.plays).slice(0, 20).map(e => `<tr>
      <td>${esc(e.series)}</td><td>${String(e.episodeNum).padStart(2, '0')}</td>
      <td>${esc(e.title)}</td><td>${'★'.repeat(Math.min(e.plays, 8))}${e.plays > 8 ? ' ×' + e.plays : ''}</td></tr>`).join('')}
  </table>` : '<p class="muted">还没有观看记录</p>';
}

/* ===================== 设备 ===================== */
const SETTING_DEFS = [
  ['dailyLimit', '每日集数上限', 'number'],
  ['threshold', '完成阈值 %', 'number'],
  ['masterStars', '“熟了”次数', 'number'],
  ['playlistSize', '片单数量', 'number'],
  ['playbackMode', '播完默认', 'select', [
    ['sequential', '顺序连播'],
    ['loop', '单集循环'],
    ['stop', '播完停止'],
  ]],
  ['wifiOnly', '仅 Wi-Fi 下载', 'checkbox'],
  ['allowDelete', '同步删除本地文件', 'checkbox'],
];

$('#newCodeBtn').addEventListener('click', async () => {
  const name = prompt('设备名称（例如：客厅电视）', '客厅电视');
  if (name === null) return;
  const r = await api('/api/admin/pairing-codes', { method: 'POST', json: { name } });
  const el = $('#codeOut');
  el.innerHTML = `配对码 <b>${r.code}</b> · ${r.expiresInMinutes} 分钟内在电视“家长设置 → 服务器与配对”里输入`;
  el.classList.remove('hidden');
});

async function loadDevices() {
  const { devices } = await api('/api/admin/devices');
  $('#deviceList').innerHTML = devices.length ? devices.map(d => `
    <div class="device" data-id="${d.id}">
      <div class="head">
        <b>📺 ${esc(d.name)}</b><span class="meta">ID ${d.id} · 最近在线 ${d.lastSeen ? new Date(d.lastSeen).toLocaleString('zh-CN') : '—'}</span>
        <span class="meta">今天 ${d.todayEps} 集 · 🔥 连续 ${d.streak} 天 · 设置版本 v${d.settingsVersion}</span>
      </div>
      <div class="sets">
        ${SETTING_DEFS.map(def => {
          const [k, label, type, options] = def;
          let input;
          if (type === 'checkbox') {
            input = `<input type="checkbox" data-k="${k}" ${d.settings[k] ? 'checked' : ''}>`;
          } else if (type === 'select') {
            const cur = d.settings[k] || (options[0] && options[0][0]) || '';
            input = `<select data-k="${k}">${options.map(([v, t]) =>
              `<option value="${v}"${v === cur ? ' selected' : ''}>${t}</option>`).join('')}</select>`;
          } else {
            input = `<input type="number" data-k="${k}" value="${d.settings[k]}">`;
          }
          return `<div class="setrow"><label>${label}</label>${input}</div>`;
        }).join('')}
      </div>
      <div class="foot">
        <button class="btn primary save">💾 保存设置（推送到电视）</button>
        <button class="btn rename">✏️ 重命名</button>
        <button class="btn danger del">🗑 删除设备</button>
      </div>
    </div>`).join('') : '<p class="muted">还没有设备。点上方「生成配对码」，然后在电视的家长设置里输入即可配对。</p>';

  document.querySelectorAll('.device .save').forEach(b => b.addEventListener('click', async () => {
    const card = b.closest('.device');
    const settings = {};
    card.querySelectorAll('[data-k]').forEach(inp => {
      settings[inp.dataset.k] = inp.type === 'checkbox' ? inp.checked
        : inp.tagName === 'SELECT' ? inp.value
        : Number(inp.value);
    });
    const r = await api(`/api/admin/devices/${card.dataset.id}`, { method: 'PATCH', json: { settings } });
    toast(`已保存，设置版本 v${r.settingsVersion}，电视下次同步时生效 ✅`);
    loadDevices();
  }));
  document.querySelectorAll('.device .rename').forEach(b => b.addEventListener('click', async () => {
    const card = b.closest('.device');
    const name = prompt('新的设备名称：'); if (!name) return;
    await api(`/api/admin/devices/${card.dataset.id}`, { method: 'PATCH', json: { name } });
    loadDevices();
  }));
  document.querySelectorAll('.device .del').forEach(b => b.addEventListener('click', async () => {
    const card = b.closest('.device');
    if (!confirm('确定删除该设备？观看记录会保留，但设备需要重新配对。')) return;
    await api(`/api/admin/devices/${card.dataset.id}`, { method: 'DELETE' });
    loadDevices();
  }));
}

/* ===================== 内容库 ===================== */
async function loadContent() {
  const { tree, seriesMeta } = await api('/api/admin/content');
  const metaOf = (l, s) => seriesMeta.find(m => m.language === l && m.folder === s);
  const langs = Object.keys(tree).sort();
  $('#contentTree').innerHTML = langs.length ? langs.map(lang => `
    <div class="lang"><b>📁 ${esc(lang)}</b>
      ${Object.keys(tree[lang]).sort().map(series => {
        const m = metaOf(lang, series);
        return `<div class="series"><b>🎬 ${esc(series)}</b>
          <span class="muted">（${tree[lang][series].length} 集${m?.cnName ? ' · ' + esc(m.cnName) : ''}${m?.hasCover ? ' · 已有封面' : ''}）</span>
          <button class="btn sm cover" data-lang="${esc(lang)}" data-series="${esc(series)}">🖼 封面</button>
          ${tree[lang][series].map(ep => `
            <div class="ep" data-id="${ep.id}">
              <span class="fn">${esc(ep.filename)}</span>
              <span class="ttl">${esc(ep.title) || '<span class="muted">（无标题）</span>'}</span>
              <span class="sz">${fmtSize(ep.size)}</span>
              <span class="sync ${ep.syncedDevices.length ? 'ok' : ''}">${ep.syncedDevices.length ? `✓ ${ep.syncedDevices.length} 台设备已同步` : '尚未同步'}</span>
              <button class="btn sm edit">✏️</button>
              <button class="btn sm preview">▶</button>
              <button class="btn sm danger del">🗑</button>
            </div>`).join('')}
        </div>`;
      }).join('')}
    </div>`).join('') : '<p class="muted">内容库是空的。用上面的表单上传第一集吧。</p>';

  document.querySelectorAll('.ep .edit').forEach(b => b.addEventListener('click', async () => {
    const id = b.closest('.ep').dataset.id;
    const title = prompt('集标题：'); if (title === null) return;
    await api(`/api/admin/content/${id}`, { method: 'PATCH', json: { title } });
    loadContent();
  }));
  document.querySelectorAll('.ep .del').forEach(b => b.addEventListener('click', async () => {
    const id = b.closest('.ep').dataset.id;
    if (!confirm('删除这一集？开启了“同步删除”的电视也会删掉本地文件。')) return;
    await api(`/api/admin/content/${id}`, { method: 'DELETE' });
    toast('已删除'); loadContent();
  }));
  document.querySelectorAll('.ep .preview').forEach(b => b.addEventListener('click', () => {
    const id = b.closest('.ep').dataset.id;
    window.open(`/api/admin/files/${id}?token=${encodeURIComponent(token)}`, '_blank');
  }));
  document.querySelectorAll('.series .cover').forEach(b => b.addEventListener('click', () => {
    const inp = document.createElement('input');
    inp.type = 'file'; inp.accept = 'image/*';
    inp.onchange = async () => {
      if (!inp.files[0]) return;
      const fd = new FormData(); fd.append('file', inp.files[0]);
      await api(`/api/admin/series-cover?language=${encodeURIComponent(b.dataset.lang)}&folder=${encodeURIComponent(b.dataset.series)}`,
        { method: 'POST', body: fd });
      toast('封面已上传 ✅'); loadContent();
    };
    inp.click();
  }));
}

/* ---------- 分块上传（4MB/块，失败自动续传） ---------- */
const CHUNK = 4 * 1024 * 1024;
async function sha256Hex(file) {
  if (file.size > 512 * 1024 * 1024) return '';   // 超大文件跳过浏览器端哈希，由服务端存储哈希
  const buf = await file.arrayBuffer();
  const d = await crypto.subtle.digest('SHA-256', buf);
  return [...new Uint8Array(d)].map(b => b.toString(16).padStart(2, '0')).join('');
}
$('#upBtn').addEventListener('click', async () => {
  const lang = $('#upLang').value.trim(), series = $('#upSeries').value.trim();
  const files = $('#upFile').files;
  if (!lang || !series || !files.length) { toast('请填写语言、动画名并选择文件'); return; }
  $('#upProg').classList.remove('hidden');
  for (let fi = 0; fi < files.length; fi++) {
    const file = files[fi];
    const label = `(${fi + 1}/${files.length}) ${file.name}`;
    try {
      $('#upMsg').textContent = `${label} 计算校验…`;
      const hash = await sha256Hex(file);
      const init = await api('/api/admin/uploads', { method: 'POST', json: {
        language: lang, series, filename: file.name,
        title: files.length === 1 ? $('#upTitle').value.trim() : '',
        totalSize: file.size, sha256: hash,
      }});
      let offset = 0, retries = 0;
      while (offset < file.size) {
        const chunk = file.slice(offset, offset + CHUNK);
        try {
          const r = await api(`/api/admin/uploads/${init.uploadId}/chunk?offset=${offset}`,
            { method: 'PUT', body: chunk, headers: { 'Content-Type': 'application/octet-stream' } });
          offset = r.received; retries = 0;
        } catch (e) {
          if (++retries > 5) throw e;
          const st = await api(`/api/admin/uploads/${init.uploadId}`);   // 续传：询问服务端进度
          offset = st.received;
          await new Promise(r => setTimeout(r, 1000 * retries));
        }
        $('#upBar').style.width = (offset / file.size * 100) + '%';
        $('#upMsg').textContent = `${label} ${fmtSize(offset)} / ${fmtSize(file.size)}`;
      }
      await api(`/api/admin/uploads/${init.uploadId}/complete`, { method: 'POST' });
      toast(`${file.name} 上传完成 ✅`);
    } catch (e) {
      toast(`${file.name} 上传失败：${e.message}`); break;
    }
  }
  $('#upBar').style.width = '0';
  $('#upProg').classList.add('hidden');
  loadContent();
});

/* ===================== 启动 ===================== */
async function boot() {
  $('#loginView').classList.add('hidden');
  $('#appView').classList.remove('hidden');
  try { await loadDash(); } catch (e) { /* 401 已处理 */ }
}
if (token) boot(); else showLogin();
