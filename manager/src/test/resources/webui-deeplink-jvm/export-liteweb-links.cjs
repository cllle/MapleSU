const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '../../../../..');
const file = process.argv[3] || path.join(root, 'manager/src/main/assets/liteweb/a/app.js');
const source = fs.readFileSync(file, 'utf8');
const start = source.indexOf('function openModuleWeb(m) {');
const end = source.indexOf('/* ═', start);
assert.ok(start >= 0 && end > start);
const actualFunction = source.slice(start, end);
const token = 'a'.repeat(64);
const urls = {};
for (const [name, id] of Object.entries({script: 'YH_YC', native: 'maplesu:0123456789abcdef0123456789abcdef'})) {
  let sheet;
  const context = { P: '/x7k9f/' + token, encodeURIComponent,
    el(tag) { return { tag, children: [], appendChild(child) { this.children.push(child); }, setAttribute() {} }; },
    openSheet(title, box) { sheet = box; },
  };
  vm.runInNewContext(actualFunction, context, { filename: file });
  context.openModuleWeb({ id, name: name + ' original page fixture' });
  urls[name] = sheet.children[0].children.find(child => child.tag === 'a').href;
}
fs.writeFileSync(process.argv[2], Object.entries(urls).map(([key,value]) => key + '=' + value).join('\n') + '\n');
console.log(JSON.stringify(urls));
