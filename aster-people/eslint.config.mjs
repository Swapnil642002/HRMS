import parser from '@typescript-eslint/parser';
export default [
  { ignores: ['**/node_modules/**', '**/dist/**', '**/.next/**', '**/build/**'] },
  {
    files: ['apps/**/*.ts', 'apps/**/*.tsx', 'scripts/**/*.mjs', 'tests/**/*.{ts,cjs}'],
    languageOptions: { parser, parserOptions: { ecmaVersion: 'latest', sourceType: 'module', ecmaFeatures: { jsx: true } } },
    rules: { 'no-debugger': 'error', 'no-dupe-args': 'error', 'no-unreachable': 'error', 'no-unsafe-finally': 'error' }
  }
];
