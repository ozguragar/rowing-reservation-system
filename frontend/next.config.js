/** @type {import('next').NextConfig} */
const securityHeaders = [
  { key: 'X-Content-Type-Options', value: 'nosniff' },
  { key: 'X-Frame-Options', value: 'SAMEORIGIN' },
  { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
  { key: 'Permissions-Policy', value: 'geolocation=(), microphone=(), camera=()' },
  // NOTE: Strict-Transport-Security should be set by the reverse proxy (nginx) in prod,
  //       not here — Next.js may be served over HTTP locally.
];

const nextConfig = {
  output: 'standalone',
  poweredByHeader: false,
  eslint: {
    ignoreDuringBuilds: true,
  },
  async headers() {
    return [
      { source: '/:path*', headers: securityHeaders },
    ];
  },
};

module.exports = nextConfig;
