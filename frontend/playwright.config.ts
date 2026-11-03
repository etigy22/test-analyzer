import { defineConfig } from '@playwright/test';
export default defineConfig({testDir:'./tests', timeout:180000, expect:{timeout:15000}, workers:1, retries:0,
  reporter:[['list'],['json',{outputFile:'test-results/e2e-results.json'}],['html',{open:'never'}]],
  use:{baseURL:process.env.REPOGUARD_TEST_URL || 'http://localhost:8088', actionTimeout:10000, viewport:{width:1440,height:1000}, screenshot:'only-on-failure', trace:'retain-on-failure'},
});
