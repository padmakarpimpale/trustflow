import { defineConfig } from "vite";
export default defineConfig({
  base: "/app/",
  build: {
    minify: false,
    cssMinify: false,
    outDir: "../src/main/resources/static/app",
    emptyOutDir: true,
  },
  server: {
    proxy: {
      "/api": "http://localhost:8080",
      "/login": "http://localhost:8080",
      "/logout": "http://localhost:8080",
    },
  },
});
