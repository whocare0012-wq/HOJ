import http from 'node:http';

const port = Number(process.env.AI_MOCK_PORT || 19001);

const server = http.createServer((request, response) => {
  if (request.method === 'GET' && request.url === '/health') {
    response.writeHead(200, { 'content-type': 'application/json' });
    response.end(JSON.stringify({ status: 'ok' }));
    return;
  }

  if (request.method !== 'POST' || request.url !== '/v1/chat/completions') {
    response.writeHead(404, { 'content-type': 'application/json' });
    response.end(JSON.stringify({ error: { message: 'not found' } }));
    return;
  }

  let body = '';
  request.setEncoding('utf8');
  request.on('data', (chunk) => {
    body += chunk;
  });
  request.on('end', () => {
    const payload = JSON.parse(body || '{}');
    const systemPrompt = payload.messages?.[0]?.content || '';
    const content = systemPrompt.includes('代码排错助手')
      ? '模拟排错结果：请检查边界条件与循环终止条件。'
      : '模拟解题提示：先提取状态，再分析转移关系与复杂度。';
    response.writeHead(200, { 'content-type': 'application/json' });
    response.end(JSON.stringify({
      id: 'mock-ai-response',
      choices: [{ message: { role: 'assistant', content } }],
    }));
  });
});

server.listen(port, '0.0.0.0', () => {
  process.stdout.write(`AI assistant mock server listening on ${port}\n`);
});
