(() => {
  const state = {
    user: null,
    documents: [],
    selectedCategory: '',
    searchQuery: '',
    encryptedOnly: false,
    protectedOnly: false,
    sort: 'newest',
    pendingUnlock: null, // { doc, mode: 'download' | 'preview' }
    currentPreviewDoc: null,
    currentPreviewPassword: null,
    previewBlobUrl: null,
  };

  // DOM helpers
  const $ = (id) => document.getElementById(id);
  const authView = $('auth-view');
  const appView = $('app-view');

  // Toast Notification
  function toast(message, type = 'info') {
    const el = document.createElement('div');
    el.className = `toast toast-${type}`;
    el.textContent = message;
    $('toast-container').appendChild(el);
    setTimeout(() => {
      el.style.opacity = '0';
      el.style.transform = 'translateX(20px)';
      el.style.transition = 'all 0.25s ease';
      setTimeout(() => el.remove(), 260);
    }, 3200);
  }

  async function api(url, options = {}) {
    const res = await fetch(url, {
      credentials: 'same-origin',
      ...options,
    });
    const contentType = res.headers.get('content-type') || '';
    let data = null;
    if (contentType.includes('application/json')) {
      data = await res.json();
    }
    if (!res.ok) {
      const msg = (data && data.error) ? data.error : `Request failed (${res.status})`;
      const err = new Error(msg);
      err.status = res.status;
      err.data = data;
      throw err;
    }
    return data;
  }

  // Formatters
  function formatBytes(bytes) {
    if (!bytes || bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${(bytes / Math.pow(k, i)).toFixed(i === 0 ? 0 : 1)} ${sizes[i]}`;
  }

  function formatDate(iso) {
    if (!iso) return '';
    return new Date(iso).toLocaleString(undefined, {
      month: 'short', day: 'numeric', year: 'numeric',
      hour: '2-digit', minute: '2-digit'
    });
  }

  function fileIcon(contentType = '', filename = '') {
    const ct = contentType.toLowerCase();
    const fn = filename.toLowerCase();
    if (ct.includes('pdf') || fn.endsWith('.pdf')) return '📄';
    if (ct.startsWith('image/')) return '🖼️';
    if (ct.includes('word') || fn.endsWith('.doc') || fn.endsWith('.docx')) return '📝';
    if (ct.includes('sheet') || ct.includes('excel') || fn.endsWith('.csv') || fn.endsWith('.xlsx')) return '📊';
    if (ct.includes('zip') || ct.includes('compressed') || fn.endsWith('.zip')) return '🗜️';
    if (ct.startsWith('text/')) return '📃';
    return '📁';
  }

  function escapeHtml(str) {
    return String(str ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  // Modal helpers
  function openModal(id) {
    $(id).classList.remove('hidden');
  }

  function closeModal(id) {
    $(id).classList.add('hidden');
    if (id === 'preview-modal' && state.previewBlobUrl) {
      URL.revokeObjectURL(state.previewBlobUrl);
      state.previewBlobUrl = null;
      $('preview-body').innerHTML = '';
    }
  }

  document.querySelectorAll('[data-close-modal]').forEach((el) => {
    el.addEventListener('click', () => closeModal(el.dataset.closeModal));
  });

  // Password visibility toggles
  document.querySelectorAll('.toggle-pw').forEach((btn) => {
    btn.addEventListener('click', () => {
      const input = $(btn.dataset.target);
      input.type = input.type === 'password' ? 'text' : 'password';
    });
  });

  // Auth Tabs
  document.querySelectorAll('.auth-tab').forEach((tab) => {
    tab.addEventListener('click', () => {
      document.querySelectorAll('.auth-tab').forEach((t) => t.classList.remove('active'));
      tab.classList.add('active');
      const isLogin = tab.dataset.authTab === 'login';
      $('login-form').classList.toggle('hidden', !isLogin);
      $('register-form').classList.toggle('hidden', isLogin);
    });
  });

  // Auth Actions
  $('login-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
      const user = await api('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: $('login-email').value.trim(),
          password: $('login-password').value,
        }),
      });
      onAuthenticated(user);
      toast(`Welcome back, ${user.fullName}!`, 'success');
    } catch (err) {
      toast(err.message, 'error');
    }
  });

  $('register-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
      const user = await api('/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          fullName: $('reg-name').value.trim(),
          email: $('reg-email').value.trim(),
          password: $('reg-password').value,
        }),
      });
      onAuthenticated(user);
      toast('Account created & vault unlocked!', 'success');
    } catch (err) {
      toast(err.message, 'error');
    }
  });

  $('logout-btn').addEventListener('click', async () => {
    await api('/api/auth/logout', { method: 'POST' });
    state.user = null;
    appView.classList.add('hidden');
    authView.classList.remove('hidden');
    $('login-password').value = '';
    toast('Signed out securely', 'info');
  });

  function onAuthenticated(user) {
    state.user = user;
    $('user-name').textContent = user.fullName;
    $('user-avatar').textContent = (user.fullName || 'U').charAt(0).toUpperCase();
    authView.classList.add('hidden');
    appView.classList.remove('hidden');
    loadDocuments();
    loadActivities();
  }

  async function init() {
    try {
      const me = await api('/api/auth/me');
      if (me && me.authenticated) {
        onAuthenticated(me);
      } else {
        authView.classList.remove('hidden');
      }
    } catch {
      authView.classList.remove('hidden');
    }
  }

  // View Tabs (Vault vs Activity)
  document.querySelectorAll('.view-tab').forEach((tab) => {
    tab.addEventListener('click', () => {
      document.querySelectorAll('.view-tab').forEach((t) => t.classList.remove('active'));
      tab.classList.add('active');
      const target = tab.dataset.viewTab;
      $('vault-section').classList.toggle('hidden', target !== 'vault');
      $('activity-section').classList.toggle('hidden', target !== 'activity');
      if (target === 'activity') loadActivities();
    });
  });

  // Vault Filters & Search
  let searchTimer = null;
  $('search-input').addEventListener('input', (e) => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => {
      state.searchQuery = e.target.value.trim();
      loadDocuments();
    }, 220);
  });

  $('filter-encrypted').addEventListener('change', (e) => {
    state.encryptedOnly = e.target.checked;
    loadDocuments();
  });

  $('filter-protected').addEventListener('change', (e) => {
    state.protectedOnly = e.target.checked;
    loadDocuments();
  });

  $('sort-select').addEventListener('change', (e) => {
    state.sort = e.target.value;
    loadDocuments();
  });

  document.querySelectorAll('.cat-pill').forEach((pill) => {
    pill.addEventListener('click', () => {
      document.querySelectorAll('.cat-pill').forEach((p) => p.classList.remove('active'));
      pill.classList.add('active');
      state.selectedCategory = pill.dataset.category;
      loadDocuments();
    });
  });

  async function loadDocuments() {
    const params = new URLSearchParams();
    if (state.searchQuery) params.set('q', state.searchQuery);
    if (state.selectedCategory) params.set('category', state.selectedCategory);
    if (state.encryptedOnly) params.set('encryptedOnly', 'true');
    if (state.protectedOnly) params.set('protectedOnly', 'true');
    params.set('sort', state.sort);

    try {
      const data = await api(`/api/documents?${params.toString()}`);
      state.documents = data.documents || [];
      renderStats(data.stats);
      renderDocuments(state.documents);
    } catch (err) {
      if (err.status === 401) {
        authView.classList.remove('hidden');
        appView.classList.add('hidden');
      } else {
        toast(err.message, 'error');
      }
    }
  }

  function renderStats(stats) {
    if (!stats) return;
    $('stat-total').textContent = stats.totalCount;
    $('stat-encrypted').textContent = stats.encryptedCount;
    $('stat-protected').textContent = stats.protectedCount;
    $('stat-storage').textContent = formatBytes(stats.totalBytes);

    $('cat-count-ALL').textContent = stats.totalCount;
    const counts = stats.categoryCounts || {};
    Object.keys(counts).forEach((cat) => {
      const el = $(`cat-count-${cat}`);
      if (el) el.textContent = counts[cat];
    });
  }

  function renderDocuments(docs) {
    const grid = $('documents-grid');
    const empty = $('empty-vault');
    grid.innerHTML = '';

    if (!docs.length) {
      empty.classList.remove('hidden');
      return;
    }
    empty.classList.add('hidden');

    docs.forEach((doc, idx) => {
      const card = document.createElement('article');
      card.className = 'doc-card glass-card';
      card.style.animationDelay = `${Math.min(idx * 45, 320)}ms`;

      const tagsHtml = (doc.tags || '')
        .split(',')
        .map((t) => t.trim())
        .filter(Boolean)
        .slice(0, 4)
        .map((t) => `<span class="tag-pill">#${escapeHtml(t)}</span>`)
        .join('');

      card.innerHTML = `
        <div class="doc-top">
          <div class="doc-icon-box">${fileIcon(doc.contentType, doc.originalFilename)}</div>
          <div class="doc-badges">
            <span class="badge badge-cat">${escapeHtml(doc.categoryLabel)}</span>
            ${doc.encrypted ? '<span class="badge badge-enc">Encrypted</span>' : ''}
            ${doc.passwordProtected ? '<span class="badge badge-lock">Locked</span>' : ''}
          </div>
        </div>
        <div>
          <h3 class="doc-title">${escapeHtml(doc.title)}</h3>
          <div class="doc-filename" title="${escapeHtml(doc.originalFilename)}">${escapeHtml(doc.originalFilename)}</div>
        </div>
        ${doc.description ? `<p class="doc-desc">${escapeHtml(doc.description)}</p>` : ''}
        ${tagsHtml ? `<div class="doc-tags">${tagsHtml}</div>` : ''}
        <div class="doc-meta">
          <span>${formatBytes(doc.fileSize)}</span>
          <span>${formatDate(doc.createdAt)}</span>
        </div>
        <div class="doc-actions">
          <button class="btn btn-ghost btn-sm" data-action="preview" data-id="${doc.id}">👁 View</button>
          <button class="btn btn-ghost btn-sm" data-action="download" data-id="${doc.id}">⬇ Save</button>
          <button class="btn btn-ghost btn-sm" data-action="edit" data-id="${doc.id}">⚙ Edit</button>
          <button class="btn btn-danger btn-sm" data-action="delete" data-id="${doc.id}">🗑</button>
        </div>
      `;

      grid.appendChild(card);
    });
  }

  // Document Card Actions
  $('documents-grid').addEventListener('click', (e) => {
    const btn = e.target.closest('button[data-action]');
    if (!btn) return;
    const id = Number(btn.dataset.id);
    const doc = state.documents.find((d) => d.id === id);
    if (!doc) return;

    const action = btn.dataset.action;
    if (action === 'preview') handleAccess(doc, 'preview');
    else if (action === 'download') handleAccess(doc, 'download');
    else if (action === 'edit') openEditModal(doc);
    else if (action === 'delete') deleteDocument(doc);
  });

  function handleAccess(doc, mode) {
    if (doc.passwordProtected) {
      state.pendingUnlock = { doc, mode };
      $('unlock-doc-name').textContent = doc.title;
      $('unlock-password').value = '';
      $('unlock-error').classList.add('hidden');
      openModal('unlock-modal');
      setTimeout(() => $('unlock-password').focus(), 80);
    } else {
      if (mode === 'preview') performPreview(doc, null);
      else performDownload(doc, null);
    }
  }

  $('unlock-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    if (!state.pendingUnlock) return;
    const { doc, mode } = state.pendingUnlock;
    const password = $('unlock-password').value;

    try {
      await api(`/api/documents/${doc.id}/verify-password`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ password }),
      });
      closeModal('unlock-modal');
      if (mode === 'preview') await performPreview(doc, password);
      else await performDownload(doc, password);
    } catch (err) {
      const errBox = $('unlock-error');
      errBox.textContent = err.message;
      errBox.classList.remove('hidden');
      const card = $('unlock-modal-card');
      card.classList.remove('shake');
      void card.offsetWidth;
      card.classList.add('shake');
    }
  });

  async function performDownload(doc, docPassword) {
    try {
      const headers = {};
      if (docPassword) headers['X-Document-Password'] = docPassword;
      const res = await fetch(`/api/documents/${doc.id}/download`, {
        credentials: 'same-origin',
        headers,
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.error || 'Download failed');
      }
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = doc.originalFilename || doc.title;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
      toast(`Downloaded "${doc.title}"`, 'success');
      loadActivities();
    } catch (err) {
      toast(err.message, 'error');
    }
  }

  async function performPreview(doc, docPassword) {
    try {
      const headers = {};
      if (docPassword) headers['X-Document-Password'] = docPassword;
      const res = await fetch(`/api/documents/${doc.id}/preview`, {
        credentials: 'same-origin',
        headers,
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.error || 'Preview failed');
      }

      const blob = await res.blob();
      const ct = (doc.contentType || blob.type || '').toLowerCase();
      state.currentPreviewDoc = doc;
      state.currentPreviewPassword = docPassword;
      $('preview-title').textContent = doc.title;
      const body = $('preview-body');
      body.innerHTML = '';

      if (ct.startsWith('image/')) {
        state.previewBlobUrl = URL.createObjectURL(blob);
        const img = document.createElement('img');
        img.src = state.previewBlobUrl;
        img.alt = doc.title;
        body.appendChild(img);
      } else if (ct.includes('pdf')) {
        state.previewBlobUrl = URL.createObjectURL(blob);
        const iframe = document.createElement('iframe');
        iframe.src = state.previewBlobUrl;
        body.appendChild(iframe);
      } else if (ct.startsWith('text/') || ct.includes('json') || ct.includes('xml')) {
        const text = await blob.text();
        const pre = document.createElement('pre');
        pre.textContent = text;
        body.appendChild(pre);
      } else {
        body.innerHTML = `
          <div class="empty-state">
            <div class="empty-icon">${fileIcon(doc.contentType, doc.originalFilename)}</div>
            <p>Inline preview is not available for <strong>${escapeHtml(doc.originalFilename)}</strong>.</p>
            <p class="muted">Click Download above to save the decrypted file.</p>
          </div>
        `;
      }

      openModal('preview-modal');
      loadActivities();
    } catch (err) {
      toast(err.message, 'error');
    }
  }

  $('preview-download-btn').addEventListener('click', () => {
    if (state.currentPreviewDoc) {
      performDownload(state.currentPreviewDoc, state.currentPreviewPassword);
    }
  });

  // Upload Modal & Drag-and-Drop
  $('open-upload-btn').addEventListener('click', openUploadModal);
  $('empty-upload-btn').addEventListener('click', openUploadModal);

  function openUploadModal() {
    $('upload-form').reset();
    $('selected-file-chip').classList.add('hidden');
    $('upload-doc-pw-wrap').classList.add('hidden');
    $('upload-progress-wrap').classList.add('hidden');
    $('upload-progress-fill').style.width = '0%';
    openModal('upload-modal');
  }

  $('upload-protect-toggle').addEventListener('change', (e) => {
    $('upload-doc-pw-wrap').classList.toggle('hidden', !e.target.checked);
    $('upload-doc-password').required = e.target.checked;
  });

  const dropZone = $('drop-zone');
  const fileInput = $('upload-file');

  function updateSelectedFile(file) {
    if (!file) return;
    $('selected-file-name').textContent = file.name;
    $('selected-file-size').textContent = formatBytes(file.size);
    $('selected-file-chip').classList.remove('hidden');
    if (!$('upload-title').value.trim()) {
      $('upload-title').value = file.name.replace(/\.[^/.]+$/, '');
    }
  }

  fileInput.addEventListener('change', () => updateSelectedFile(fileInput.files[0]));

  ['dragenter', 'dragover'].forEach((evt) => {
    dropZone.addEventListener(evt, (e) => {
      e.preventDefault();
      dropZone.classList.add('drag-over');
    });
  });

  ['dragleave', 'drop'].forEach((evt) => {
    dropZone.addEventListener(evt, (e) => {
      e.preventDefault();
      dropZone.classList.remove('drag-over');
    });
  });

  dropZone.addEventListener('drop', (e) => {
    const files = e.dataTransfer.files;
    if (files && files.length) {
      fileInput.files = files;
      updateSelectedFile(files[0]);
    }
  });

  $('upload-form').addEventListener('submit', (e) => {
    e.preventDefault();
    const file = fileInput.files[0];
    if (!file) {
      toast('Please select a file', 'error');
      return;
    }

    const formData = new FormData();
    formData.append('file', file);
    formData.append('title', $('upload-title').value.trim() || file.name);
    formData.append('category', $('upload-category').value);
    formData.append('tags', $('upload-tags').value.trim());
    formData.append('description', $('upload-description').value.trim());
    formData.append('encrypt', $('upload-encrypt').checked ? 'true' : 'false');
    if ($('upload-protect-toggle').checked) {
      formData.append('documentPassword', $('upload-doc-password').value);
    }

    const progressWrap = $('upload-progress-wrap');
    const progressFill = $('upload-progress-fill');
    const submitBtn = $('upload-submit-btn');
    progressWrap.classList.remove('hidden');
    submitBtn.disabled = true;

    const xhr = new XMLHttpRequest();
    xhr.open('POST', '/api/documents', true);
    xhr.withCredentials = true;

    xhr.upload.onprogress = (evt) => {
      if (evt.lengthComputable) {
        const pct = Math.round((evt.loaded / evt.total) * 100);
        progressFill.style.width = `${pct}%`;
      }
    };

    xhr.onload = () => {
      submitBtn.disabled = false;
      if (xhr.status >= 200 && xhr.status < 300) {
        closeModal('upload-modal');
        toast('Document uploaded', 'success');
        loadDocuments();
        loadActivities();
      } else {
        let msg = 'Upload failed';
        try { msg = JSON.parse(xhr.responseText).error || msg; } catch {}
        toast(msg, 'error');
        progressWrap.classList.add('hidden');
      }
    };

    xhr.onerror = () => {
      submitBtn.disabled = false;
      progressWrap.classList.add('hidden');
      toast('Network error during upload', 'error');
    };

    xhr.send(formData);
  });

  // Edit / Security Modal
  function openEditModal(doc) {
    $('edit-doc-id').value = doc.id;
    $('edit-title').value = doc.title;
    $('edit-category').value = doc.category;
    $('edit-tags').value = doc.tags || '';
    $('edit-description').value = doc.description || '';
    $('edit-current-password').value = '';
    $('edit-new-password').value = '';
    $('edit-remove-password').checked = false;

    $('edit-current-pw-wrap').classList.toggle('hidden', !doc.passwordProtected);
    $('edit-remove-pw-wrap').classList.toggle('hidden', !doc.passwordProtected);
    openModal('edit-modal');
  }

  $('edit-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const id = $('edit-doc-id').value;
    const payload = {
      title: $('edit-title').value.trim(),
      category: $('edit-category').value,
      tags: $('edit-tags').value.trim(),
      description: $('edit-description').value.trim(),
      currentPassword: $('edit-current-password').value || null,
      newPassword: $('edit-new-password').value || null,
      removePassword: $('edit-remove-password').checked,
    };

    try {
      await api(`/api/documents/${id}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      closeModal('edit-modal');
      toast('Document updated', 'success');
      loadDocuments();
      loadActivities();
    } catch (err) {
      toast(err.message, 'error');
    }
  });

  // Delete Document
  async function deleteDocument(doc) {
    if (!confirm(`Permanently delete "${doc.title}" from your vault?`)) return;
    try {
      await api(`/api/documents/${doc.id}`, { method: 'DELETE' });
      toast(`Deleted "${doc.title}"`, 'info');
      loadDocuments();
      loadActivities();
    } catch (err) {
      toast(err.message, 'error');
    }
  }

  // Activity History
  $('activity-filter').addEventListener('change', loadActivities);

  async function loadActivities() {
    const action = $('activity-filter').value;
    const params = new URLSearchParams({ limit: '100' });
    if (action) params.set('action', action);

    try {
      const items = await api(`/api/activities?${params.toString()}`);
      const list = $('activity-list');
      const empty = $('empty-activity');
      list.innerHTML = '';

      if (!items.length) {
        empty.classList.remove('hidden');
        return;
      }
      empty.classList.add('hidden');

      items.forEach((act, idx) => {
        const row = document.createElement('div');
        row.className = 'activity-item glass-card';
        row.style.animationDelay = `${Math.min(idx * 25, 250)}ms`;
        row.innerHTML = `
          <div class="activity-left">
            <span class="act-badge act-${escapeHtml(act.action)}">${escapeHtml(act.action.replace('_', ' '))}</span>
            <div>
              <div><strong>${escapeHtml(act.documentTitle)}</strong></div>
              <div class="muted">${escapeHtml(act.details || '')}</div>
            </div>
          </div>
          <div class="activity-time">${formatDate(act.createdAt)}</div>
        `;
        list.appendChild(row);
      });
    } catch {
      // Ignore if not authenticated yet
    }
  }

  init();
})();
