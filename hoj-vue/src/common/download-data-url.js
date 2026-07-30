export function downloadDataUrl(dataUrl, filename) {
  const [metadata, encodedData] = dataUrl.split(',');
  const mimeType = metadata.match(/data:([^;]+)/)?.[1] || 'application/octet-stream';
  const binary = metadata.includes(';base64')
    ? window.atob(encodedData)
    : decodeURIComponent(encodedData);
  const bytes = new Uint8Array(binary.length);

  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }

  const objectUrl = window.URL.createObjectURL(
    new window.Blob([bytes], { type: mimeType })
  );
  const link = document.createElement('a');
  link.download = filename;
  link.href = objectUrl;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => window.URL.revokeObjectURL(objectUrl), 0);
}
