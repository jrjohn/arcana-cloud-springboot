import { APP_BASE_HREF } from '@angular/common';
import { CommonEngine } from '@angular/ssr';
import express from 'express';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';
import bootstrap from './src/main.server';

/**
 * Angular Universal SSR Server
 *
 * Express server for server-side rendering Angular applications.
 * Can be called from Spring Boot's SSR Engine.
 */
export function app(): express.Express {
  const server = express();
  const serverDistFolder = dirname(fileURLToPath(import.meta.url));
  const browserDistFolder = resolve(serverDistFolder, '../browser');
  const indexHtml = join(serverDistFolder, 'index.server.html');

  const commonEngine = new CommonEngine();

  server.set('view engine', 'html');
  server.set('views', browserDistFolder);

  // Serve static files from /browser
  server.get(
    '*.*',
    express.static(browserDistFolder, {
      maxAge: '1y',
    })
  );

  // SSR API endpoint for Spring Boot integration
  server.post('/angular/render', express.json(), async (req, res) => {
    const { component, props, context } = req.body;

    try {
      const html = await commonEngine.render({
        bootstrap,
        documentFilePath: indexHtml,
        url: context?.url || '/',
        publicPath: browserDistFolder,
        providers: [
          { provide: APP_BASE_HREF, useValue: '/' },
          { provide: 'SSR_PROPS', useValue: props || {} },
          { provide: 'SSR_CONTEXT', useValue: context || {} },
        ],
      });

      res.send(html);
    } catch (error) {
      console.error('SSR Error:', error);
      res.status(500).send('Server-side rendering failed');
    }
  });

  // All regular routes use the Angular engine
  server.get('*', (req, res, next) => {
    const { protocol, originalUrl, baseUrl, headers } = req;

    commonEngine
      .render({
        bootstrap,
        documentFilePath: indexHtml,
        url: `${protocol}://${headers.host}${originalUrl}`,
        publicPath: browserDistFolder,
        providers: [{ provide: APP_BASE_HREF, useValue: baseUrl }],
      })
      .then((html) => res.send(html))
      .catch((err) => next(err));
  });

  return server;
}

function run(): void {
  const port = process.env['PORT'] || 4000;

  // Start up the Node server
  const server = app();
  server.listen(port, () => {
    console.log(`Angular SSR server listening on http://localhost:${port}`);
  });
}

run();
