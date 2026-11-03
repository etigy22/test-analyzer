import tsParser from '/opt/eslint/node_modules/@typescript-eslint/parser/dist/index.js';
export default [{
  files: ['**/*.{js,jsx,ts,tsx,mjs,cjs}'],
  languageOptions: {parser: tsParser, parserOptions: {project: false, projectService: false, ecmaFeatures: {jsx: true}}, ecmaVersion: 'latest', sourceType: 'module'},
  rules: {'no-eval':'error','no-implied-eval':'error','no-new-func':'error','no-script-url':'error','no-prototype-builtins':'warn','no-unreachable':'warn','eqeqeq':'warn','no-empty':'warn','no-constant-condition':'warn'}
}];
