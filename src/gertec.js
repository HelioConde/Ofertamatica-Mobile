const ID = '#tc406|6.5.1';
const validEAN = s => /^(?:\d{8}|\d{12}|\d{13}|\d{14})$/.test(s);
function parseResponse(raw) {
  const s = raw.replace(/[\x00-\x1f\x7f]/g, '').trim();
  if (s === '#nfound') return { status: 'not_found' };
  // Legacy Gertec: price first, opaque display-control bytes, then description.
  // Do not interpret the bytes between the price and the separator as text.
  const legacy = /^#\s*PRE[CÇ]O\s*:\s*R\$\s*((?:\d{1,3}(?:\.\d{3})+|\d+),\d{2})(?=\s|\||[^\x20-\x7e])[^#|]*\|([^#|]+)$/i.exec(s);
  if (legacy) {
    const name = legacy[2].trim();
    const cents = Number(legacy[1].replace(/[.,]/g, ''));
    if (!name || !Number.isSafeInteger(cents)) return null;
    return { status: 'found', name, cents, price: 'R$ ' + legacy[1] };
  }
  // Never treat a malformed price-first frame as a product name.
  if (/^#\s*PRE[CÇ]O\s*:/i.test(s)) return null;
  const match = /^#([^#|]+)\|\s*(R\$\s*(?:\d{1,3}(?:\.\d{3})+|\d+),\d{2}|SEM PRE[CÇ]O)\s*$/i.exec(s);
  if (!match) return null;
  const name = match[1].trim();
  if (!name) return null;
  if (/^SEM/i.test(match[2])) return { status: 'no_price', name };
  const price = match[2].replace(/^R\$\s*/i, '');
  const cents = Number(price.replace(/[.,]/g, ''));
  if (!Number.isSafeInteger(cents)) return null;
  return { status: 'found', name, cents, price: 'R$ ' + price };
}
function queryGertec(createConnection, {host, port, ean, timeoutMs = 12000, settleMs = 500, identifyDelayMs = 3000}) {
  if (!validEAN(ean)) return Promise.reject(new Error('Código de barras inválido.'));
  if (!host || !Number.isInteger(port) || port < 1 || port > 65535) return Promise.reject(new Error('Servidor ou porta inválidos.'));
  return new Promise((resolve, reject) => {
    let socket, done = false, connected = false, received = 0, stage = 'hello', buffer = '', idle, identifyTimer, deadline;
    function diagnostic() {
      if (!connected && !received) return 'TCP não conectado. Confira Wi-Fi, IP, porta e permissão de rede.';
      if (stage === 'hello') return 'TCP conectado, mas sem saudação #ok reconhecida (' + received + ' bytes recebidos).';
      if (stage === 'identified') return 'Identificação enviada, consulta ainda não iniciada.';
      if (!buffer.trim()) return 'Identificação enviada; EAN enviado, sem resposta de produto.';
      return 'EAN enviado; resposta recebida, mas formato não reconhecido (' + buffer.length + ' caracteres).';
    }
    function finish(error, result) {
      if (done) return;
      done = true;
      clearTimeout(idle); clearTimeout(identifyTimer); clearTimeout(deadline);
      try { socket?.destroy(); } catch {}
      if (error) reject(error); else resolve(result);
    }
    function sendEAN() {
      if (done || stage !== 'identified') return;
      clearTimeout(identifyTimer); stage = 'query';
      try { socket.write('#' + ean); } catch (e) { finish(e); }
    }
    function process() {
      if (done) return;
      buffer = buffer.replace(/[\x00-\x1f\x7f]/g, '').trimStart();
      if (stage === 'hello') {
        if (!buffer.startsWith('#ok')) return;
        buffer = buffer.slice(3); stage = 'identified';
        try { socket.write(ID); } catch (e) { finish(e); return; }
        identifyTimer = setTimeout(sendEAN, identifyDelayMs);
      }
      while (buffer.startsWith('#alwayslive') || buffer.startsWith('#live?')) {
        if (buffer.startsWith('#alwayslive')) { buffer = buffer.slice(11); sendEAN(); }
        else { buffer = buffer.slice(6); try { socket.write('#live'); } catch (e) { finish(e); return; } }
      }
      if (stage === 'query') {
        const result = parseResponse(buffer);
        if (result) finish(null, result);
      }
    }
    deadline = setTimeout(() => finish(new Error('Tempo limite: ' + diagnostic())), timeoutMs);
    try {
      socket = createConnection({host, port});
      socket.on('connect', () => { connected = true; });
      socket.on('data', chunk => {
        if (done) return;
        connected = true;
        received += chunk.length;
        buffer += chunk.toString('latin1');
        if (buffer.length > 4096) { finish(new Error('Resposta excedeu o limite.')); return; }
        clearTimeout(idle); idle = setTimeout(process, settleMs);
      });
      socket.on('error', e => finish(new Error('Falha na conexão: ' + e.message)));
      socket.on('close', () => { process(); if (!done) finish(new Error('Conexão encerrada: ' + diagnostic())); });
    } catch (e) { finish(e); }
  });
}
module.exports = { ID, validEAN, parseResponse, queryGertec };
