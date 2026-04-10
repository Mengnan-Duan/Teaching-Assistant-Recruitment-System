<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isErrorPage="true" %>
<!DOCTYPE html>
<html lang="en" class="smartta-shell">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Error · Smart-TA</title>
    <link rel="stylesheet" href="css/smartta-shell.css" />
    <style>
        body.smartta-shell {
            display: flex;
            align-items: center;
            justify-content: center;
            font-family: system-ui, -apple-system, "Segoe UI", sans-serif;
            color: #1a1a2e;
            padding: 1rem;
        }
        .err-card {
            max-width: 520px;
            width: 100%;
            background: #fff;
            border-radius: 14px;
            padding: 2rem 1.75rem;
            box-shadow: 0 12px 40px rgba(26, 26, 46, 0.12);
            border: 1px solid #e0ddd8;
        }
        .err-card h1 { font-size: 1.35rem; margin-bottom: 0.75rem; }
        .err-card a { color: #457b9d; font-weight: 600; text-decoration: none; }
    </style>
</head>
<body class="smartta-shell">
    <div class="err-card">
        <h1>Something went wrong</h1>
        <p><a href="index.jsp">Return to home</a></p>
    </div>
</body>
</html>
