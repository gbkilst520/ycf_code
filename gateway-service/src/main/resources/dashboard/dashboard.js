(function () {
  'use strict';

  const $ = (id) => document.getElementById(id);

  const nodes = {
    refreshOverviewBtn: $('refreshOverviewBtn'),

    reqMethod: $('reqMethod'),
    reqPath: $('reqPath'),
    reqTimeoutMs: $('reqTimeoutMs'),
    reqHeaders: $('reqHeaders'),
    reqBody: $('reqBody'),
    grayHeaderEnabled: $('grayHeaderEnabled'),
    grayHeaderValue: $('grayHeaderValue'),
    autoRequestIdEnabled: $('autoRequestIdEnabled'),
    cookieName: $('cookieName'),
    cookieValue: $('cookieValue'),
    setCookieBtn: $('setCookieBtn'),
    clearCookieBtn: $('clearCookieBtn'),
    sendReqBtn: $('sendReqBtn'),
    requestMeta: $('requestMeta'),
    responseMeta: $('responseMeta'),
    responseHeaders: $('responseHeaders'),
    responseBody: $('responseBody'),

    loadMethod: $('loadMethod'),
    loadPath: $('loadPath'),
    loadQps: $('loadQps'),
    loadDuration: $('loadDuration'),
    loadConcurrency: $('loadConcurrency'),
    startLoadBtn: $('startLoadBtn'),
    stopLoadBtn: $('stopLoadBtn'),
    loadState: $('loadState'),
    loadSent: $('loadSent'),
    loadSuccess: $('loadSuccess'),
    loadClientErr: $('loadClientErr'),
    loadServerErr: $('loadServerErr'),
    loadAvgRt: $('loadAvgRt'),
    loadP95Rt: $('loadP95Rt'),
    loadLog: $('loadLog'),

    refreshMetricsBtn: $('refreshMetricsBtn'),
    metricsTime: $('metricsTime'),
    mQps: $('mQps'),
    mRt: $('mRt'),
    mErrRate: $('mErrRate'),
    mReqTotal: $('mReqTotal'),
    mErrTotal: $('mErrTotal'),
    mLatencyCount: $('mLatencyCount'),
    metricsRaw: $('metricsRaw'),

    refreshRoutesBtn: $('refreshRoutesBtn'),
    refreshServicesBtn: $('refreshServicesBtn'),
    routesBody: $('routesBody'),
    servicesBody: $('servicesBody'),

    routesYaml: $('routesYaml'),
    applyRoutesBtn: $('applyRoutesBtn'),
    routeApplyMeta: $('routeApplyMeta'),
    routeApplyResp: $('routeApplyResp')
  };

  const metricsState = {
    lastTs: 0,
    lastReq: 0,
    lastErr: 0,
    lastLatencyCount: 0,
    lastLatencySumSeconds: 0
  };

  let loadJob = null;

  function parseHeaderLines(text) {
    const headers = {};
    if (!text || !text.trim()) {
      return headers;
    }
    const lines = text.split('\n');
    for (const rawLine of lines) {
      const line = rawLine.trim();
      if (!line || line.startsWith('#')) {
        continue;
      }
      const idx = line.indexOf(':');
      if (idx < 1) {
        continue;
      }
      const key = line.slice(0, idx).trim();
      const value = line.slice(idx + 1).trim();
      if (key) {
        headers[key] = value;
      }
    }
    return headers;
  }

  function randomRequestId() {
    return `dash-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
  }

  function normalizePath(path) {
    if (!path || !path.trim()) {
      return '/';
    }
    const value = path.trim();
    return value.startsWith('/') ? value : `/${value}`;
  }

  function formatMs(value) {
    if (!Number.isFinite(value)) {
      return '0 ms';
    }
    return `${value.toFixed(2)} ms`;
  }

  function percentile(sorted, p) {
    if (!sorted.length) {
      return 0;
    }
    const pos = Math.ceil((p / 100) * sorted.length) - 1;
    const idx = Math.max(0, Math.min(sorted.length - 1, pos));
    return sorted[idx];
  }

  async function sendRequest(method, path, headers, body, timeoutMs) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort('timeout'), timeoutMs);
    const start = performance.now();
    try {
      const requestInit = {
        method,
        headers,
        credentials: 'same-origin',
        signal: controller.signal
      };
      if (body && !['GET', 'DELETE', 'HEAD'].includes(method)) {
        requestInit.body = body;
      }
      const response = await fetch(path, requestInit);
      const duration = performance.now() - start;
      const responseText = await response.text();
      return {
        ok: true,
        status: response.status,
        statusText: response.statusText,
        duration,
        body: responseText,
        headers: response.headers
      };
    } catch (error) {
      return {
        ok: false,
        status: 0,
        statusText: String(error),
        duration: performance.now() - start,
        body: String(error),
        headers: new Headers()
      };
    } finally {
      clearTimeout(timeout);
    }
  }

  async function handleSingleRequest() {
    nodes.sendReqBtn.disabled = true;
    nodes.requestMeta.textContent = '发送中...';

    const method = nodes.reqMethod.value;
    const path = normalizePath(nodes.reqPath.value);
    const timeoutMs = Math.max(100, Number(nodes.reqTimeoutMs.value || 5000));
    const headers = parseHeaderLines(nodes.reqHeaders.value);

    if (nodes.grayHeaderEnabled.checked) {
      headers['X-Gray'] = nodes.grayHeaderValue.value || 'canary';
    }

    if (nodes.autoRequestIdEnabled.checked && !headers['X-Request-Id']) {
      headers['X-Request-Id'] = randomRequestId();
    }

    const body = nodes.reqBody.value || '';
    const result = await sendRequest(method, path, headers, body, timeoutMs);

    nodes.requestMeta.textContent = `完成于 ${new Date().toLocaleTimeString()}`;
    const size = new TextEncoder().encode(result.body || '').length;
    nodes.responseMeta.textContent = `状态: ${result.status} ${result.statusText} | 耗时: ${formatMs(result.duration)} | 大小: ${size} 字节`;

    const headerLines = [];
    result.headers.forEach((value, key) => {
      headerLines.push(`${key}: ${value}`);
    });
    nodes.responseHeaders.textContent = headerLines.length ? headerLines.join('\n') : '（无响应头）';

    const text = result.body || '';
    const contentType = result.headers.get('content-type') || '';
    if (contentType.includes('application/json')) {
      try {
        nodes.responseBody.textContent = JSON.stringify(JSON.parse(text), null, 2);
      } catch (_error) {
        nodes.responseBody.textContent = text;
      }
    } else {
      nodes.responseBody.textContent = text;
    }

    nodes.sendReqBtn.disabled = false;
  }

  function setCookie() {
    const name = (nodes.cookieName.value || '').trim();
    if (!name) {
      nodes.requestMeta.textContent = 'Cookie 名称不能为空';
      return;
    }
    const value = nodes.cookieValue.value || '';
    document.cookie = `${encodeURIComponent(name)}=${encodeURIComponent(value)}; path=/`;
    nodes.requestMeta.textContent = `已设置 Cookie: ${name}`;
  }

  function clearCookie() {
    const name = (nodes.cookieName.value || '').trim();
    if (!name) {
      nodes.requestMeta.textContent = 'Cookie 名称不能为空';
      return;
    }
    document.cookie = `${encodeURIComponent(name)}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
    nodes.requestMeta.textContent = `已清除 Cookie: ${name}`;
  }

  function startLoadTest() {
    if (loadJob) {
      return;
    }

    const method = nodes.loadMethod.value;
    const path = normalizePath(nodes.loadPath.value);
    const qps = Math.max(1, Number(nodes.loadQps.value || 1));
    const durationSec = Math.max(1, Number(nodes.loadDuration.value || 1));
    const concurrency = Math.max(1, Number(nodes.loadConcurrency.value || 1));
    const requestBody = nodes.reqBody.value || '';
    const baseHeaders = parseHeaderLines(nodes.reqHeaders.value);

    if (nodes.grayHeaderEnabled.checked) {
      baseHeaders['X-Gray'] = nodes.grayHeaderValue.value || 'canary';
    }

    const intervalMs = Math.max(1, Math.floor(1000 / qps));
    const stopAt = performance.now() + durationSec * 1000;

    const stats = {
      sent: 0,
      success: 0,
      clientErr: 0,
      serverErr: 0,
      durations: [],
      inflight: 0
    };

    nodes.startLoadBtn.disabled = true;
    nodes.stopLoadBtn.disabled = false;
    nodes.loadState.textContent = `运行中 | 路径=${path} | qps=${qps} | 时长=${durationSec}s | 并发=${concurrency}`;
    nodes.loadLog.textContent = '压测运行中...';

    let timerId = 0;
    let renderId = 0;
    let stopped = false;

    function render() {
      const durations = stats.durations.slice().sort((a, b) => a - b);
      const avg = durations.length ? durations.reduce((a, b) => a + b, 0) / durations.length : 0;
      const p95 = percentile(durations, 95);

      nodes.loadSent.textContent = String(stats.sent);
      nodes.loadSuccess.textContent = String(stats.success);
      nodes.loadClientErr.textContent = String(stats.clientErr);
      nodes.loadServerErr.textContent = String(stats.serverErr);
      nodes.loadAvgRt.textContent = formatMs(avg);
      nodes.loadP95Rt.textContent = formatMs(p95);
    }

    function stop(reason) {
      if (stopped) {
        return;
      }
      stopped = true;
      clearInterval(timerId);
      clearInterval(renderId);
      loadJob = null;
      nodes.startLoadBtn.disabled = false;
      nodes.stopLoadBtn.disabled = true;
      render();
      nodes.loadState.textContent = `已停止: ${reason}`;
      nodes.loadLog.textContent = [
        `原因: ${reason}`,
        `发送总数=${stats.sent}`,
        `成功数=${stats.success}`,
        `客户端错误=${stats.clientErr}`,
        `服务端错误=${stats.serverErr}`,
        `进行中请求=${stats.inflight}`,
        `时间=${new Date().toLocaleTimeString()}`
      ].join('\n');
    }

    async function dispatchOne() {
      stats.inflight += 1;
      stats.sent += 1;
      const headers = { ...baseHeaders };
      if (nodes.autoRequestIdEnabled.checked) {
        headers['X-Request-Id'] = randomRequestId();
      }
      const result = await sendRequest(method, path, headers, requestBody, 15000);
      stats.durations.push(result.duration);
      if (result.status >= 500 || result.status === 0) {
        stats.serverErr += 1;
      } else if (result.status >= 400) {
        stats.clientErr += 1;
      } else {
        stats.success += 1;
      }
      stats.inflight -= 1;

      if (performance.now() >= stopAt && stats.inflight === 0) {
        stop('达到设定时长');
      }
    }

    timerId = setInterval(() => {
      const now = performance.now();
      if (now >= stopAt && stats.inflight === 0) {
        stop('达到设定时长');
        return;
      }
      if (now >= stopAt) {
        return;
      }
      const room = concurrency - stats.inflight;
      for (let i = 0; i < room; i++) {
        dispatchOne();
      }
    }, intervalMs);

    renderId = setInterval(render, 500);

    loadJob = {
      stop: () => stop('手动停止')
    };
  }

  function stopLoadTest() {
    if (loadJob) {
      loadJob.stop();
    }
  }

  async function loadRoutes() {
    const response = await fetch('/admin/api/routes');
    const data = await response.json();
    const rows = data.routes || [];
    nodes.routesBody.innerHTML = rows.map((route) => {
      return `<tr>
        <td>${escapeHtml(route.id)}</td>
        <td>${escapeHtml(route.path)}</td>
        <td>${escapeHtml(route.serviceId)}</td>
        <td>${route.stripPrefix}</td>
        <td>${escapeHtml(route.loadBalance)}</td>
      </tr>`;
    }).join('');
  }

  async function loadServices() {
    const response = await fetch('/admin/api/services');
    const data = await response.json();
    const rows = data.services || [];
    nodes.servicesBody.innerHTML = rows.map((service) => {
      const instances = (service.instances || []).map((item) => escapeHtml(item.baseUrl)).join('<br/>') || '-';
      return `<tr>
        <td>${escapeHtml(service.serviceId)}</td>
        <td>${service.staticBaseUrl ? escapeHtml(service.staticBaseUrl) : '-'}</td>
        <td>${instances}</td>
      </tr>`;
    }).join('');
  }

  async function applyRoutesYaml() {
    nodes.applyRoutesBtn.disabled = true;
    nodes.routeApplyMeta.textContent = 'updating routes...';

    const response = await fetch('/admin/api/routes', {
      method: 'POST',
      headers: { 'Content-Type': 'text/plain; charset=utf-8' },
      body: nodes.routesYaml.value || ''
    });

    const text = await response.text();
    nodes.routeApplyResp.textContent = text;
    nodes.routeApplyMeta.textContent = `${response.status} ${response.statusText}`;
    nodes.applyRoutesBtn.disabled = false;

    await Promise.all([loadRoutes(), loadServices()]);
  }

  function parsePrometheusSnapshot(text) {
    const result = {
      reqTotal: 0,
      errTotal: 0,
      latencyCount: 0,
      latencySumSeconds: 0
    };

    const lines = (text || '').split('\n');
    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) {
        continue;
      }

      const match = trimmed.match(/^([a-zA-Z_:][a-zA-Z0-9_:]*)(\{[^}]*\})?\s+([-+]?\d+(?:\.\d+)?(?:[eE][-+]?\d+)?)$/);
      if (!match) {
        continue;
      }

      const name = match[1];
      const value = Number(match[3]);
      if (!Number.isFinite(value)) {
        continue;
      }

      if (name === 'gateway_requests_total') {
        result.reqTotal += value;
      } else if (name === 'gateway_error_total') {
        result.errTotal += value;
      } else if (name === 'gateway_request_latency_ms_seconds_count') {
        result.latencyCount += value;
      } else if (name === 'gateway_request_latency_ms_seconds_sum') {
        result.latencySumSeconds += value;
      }
    }

    return result;
  }

  async function refreshMetrics() {
    const response = await fetch('/metrics/prometheus');
    const raw = await response.text();

    nodes.metricsRaw.textContent = raw;
    nodes.metricsTime.textContent = `刷新于 ${new Date().toLocaleTimeString()}`;

    const snap = parsePrometheusSnapshot(raw);
    const now = Date.now();

    let qps = 0;
    let errRate = 0;
    let avgRtMs = 0;

    if (metricsState.lastTs > 0) {
      const sec = (now - metricsState.lastTs) / 1000;
      const dReq = Math.max(0, snap.reqTotal - metricsState.lastReq);
      const dErr = Math.max(0, snap.errTotal - metricsState.lastErr);
      const dCount = Math.max(0, snap.latencyCount - metricsState.lastLatencyCount);
      const dSum = Math.max(0, snap.latencySumSeconds - metricsState.lastLatencySumSeconds);

      if (sec > 0) {
        qps = dReq / sec;
      }
      if (dReq > 0) {
        errRate = (dErr / dReq) * 100;
      }
      if (dCount > 0) {
        avgRtMs = (dSum / dCount) * 1000;
      }
    }

    metricsState.lastTs = now;
    metricsState.lastReq = snap.reqTotal;
    metricsState.lastErr = snap.errTotal;
    metricsState.lastLatencyCount = snap.latencyCount;
    metricsState.lastLatencySumSeconds = snap.latencySumSeconds;

    nodes.mQps.textContent = qps.toFixed(2);
    nodes.mRt.textContent = formatMs(avgRtMs);
    nodes.mErrRate.textContent = `${errRate.toFixed(2)}%`;
    nodes.mReqTotal.textContent = Math.round(snap.reqTotal).toString();
    nodes.mErrTotal.textContent = Math.round(snap.errTotal).toString();
    nodes.mLatencyCount.textContent = Math.round(snap.latencyCount).toString();

    setStatusClass(nodes.mErrRate, errRate);
  }

  function setStatusClass(node, errRate) {
    node.classList.remove('status-ok', 'status-warn', 'status-danger');
    if (errRate < 1) {
      node.classList.add('status-ok');
    } else if (errRate < 5) {
      node.classList.add('status-warn');
    } else {
      node.classList.add('status-danger');
    }
  }

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  async function refreshOverview() {
    await Promise.all([loadRoutes(), loadServices(), refreshMetrics()]);
  }

  function bindEvents() {
    nodes.sendReqBtn.addEventListener('click', handleSingleRequest);
    nodes.setCookieBtn.addEventListener('click', setCookie);
    nodes.clearCookieBtn.addEventListener('click', clearCookie);

    nodes.startLoadBtn.addEventListener('click', startLoadTest);
    nodes.stopLoadBtn.addEventListener('click', stopLoadTest);

    nodes.refreshMetricsBtn.addEventListener('click', refreshMetrics);
    nodes.refreshRoutesBtn.addEventListener('click', loadRoutes);
    nodes.refreshServicesBtn.addEventListener('click', loadServices);
    nodes.applyRoutesBtn.addEventListener('click', applyRoutesYaml);
    nodes.refreshOverviewBtn.addEventListener('click', refreshOverview);
  }

  async function bootstrap() {
    bindEvents();
    try {
      await refreshOverview();
      nodes.requestMeta.textContent = '就绪';
    } catch (error) {
      nodes.requestMeta.textContent = `初始化失败: ${String(error)}`;
    }

    setInterval(() => {
      refreshMetrics().catch((error) => {
        nodes.metricsTime.textContent = `指标刷新失败: ${String(error)}`;
      });
    }, 4000);
  }

  bootstrap();
})();
