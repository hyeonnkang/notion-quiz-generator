document.getElementById('sendBtn').addEventListener('click', async () => {
    const status = document.getElementById('status');
    status.innerText = "분석 중...";

    const escapeHtml = (text) => text
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');

    let [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    const url = tab?.url || "";

    const cleanUrl = url.split('?')[0].split('#')[0];
    let pageId = cleanUrl.split('-').pop() || "";
    pageId = pageId.replace(/[^a-fA-F0-9]/g, '');

    if (pageId.length === 32) {
        try {
            const response = await fetch('http://localhost:8080/api/quiz/generate', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ pageId })
            });

            const raw = await response.text();

            if (!response.ok) {
                let errorText = raw;
                try {
                    const parsed = JSON.parse(raw);
                    errorText = parsed.message || parsed.error || raw;
                } catch (_) {
                    // body가 JSON이 아니면 그냥 둔다
                }
                status.innerText = `실패(${response.status}): ${errorText}`;
                return;
            }

            let renderedText = raw;
            try {
                const parsed = JSON.parse(raw);
                renderedText = JSON.stringify(parsed, null, 2);
            } catch (_) {
                // body가 JSON이 아니면 그냥 둔다
            }

            const pageHtml = `<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Quiz Response</title>
    <style>
        body {
            margin: 0;
            padding: 24px;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Noto Sans KR", Arial, sans-serif;
            background: #f6f8fa;
            color: #111827;
        }
        h1 {
            margin: 0 0 16px 0;
            font-size: 20px;
            font-weight: 700;
        }
        pre {
            margin: 0;
            padding: 16px;
            border: 1px solid #d0d7de;
            border-radius: 8px;
            background: #ffffff;
            line-height: 1.5;
            white-space: pre-wrap;
            word-break: break-word;
            font-size: 13px;
            overflow: auto;
        }
    </style>
</head>
<body>
    <h1>Quiz Response</h1>
    <pre>${escapeHtml(renderedText)}</pre>
</body>
</html>`;

            const pageUrl = `data:text/html;charset=utf-8,${encodeURIComponent(pageHtml)}`;
            await chrome.tabs.create({ url: pageUrl });
            status.innerText = "응답 창을 열었습니다.";
        } catch (error) {
            status.innerText = "에러: 서버가 꺼져있거나 CORS/네트워크 문제가 있습니다.";
        }
    } else {
        status.innerText = "노션 페이지가 아닌 것 같습니다.";
    }
});
