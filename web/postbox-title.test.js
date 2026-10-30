const assert = require('node:assert/strict');
const test = require('node:test');

const formatPostBoxManagementInfo = require('./postbox-title.js');

test('formats management office and reference together', () => {
    assert.equal(formatPostBoxManagementInfo('新宿郵便局', '00123'), '（新宿郵便局：00123）');
});

test('formats either available value without an extra separator', () => {
    assert.equal(formatPostBoxManagementInfo('新宿郵便局', ''), '（新宿郵便局）');
    assert.equal(formatPostBoxManagementInfo('', '00123'), '（00123）');
});

test('omits missing or blank management information', () => {
    assert.equal(formatPostBoxManagementInfo(null, undefined), '');
    assert.equal(formatPostBoxManagementInfo('  ', '\t'), '');
});

test('keeps long and HTML-like tag values as plain text', () => {
    const branch = '<b>とても長い管理局名</b>';
    assert.equal(formatPostBoxManagementInfo(branch, '00000000000000000001'), `（${branch}：00000000000000000001）`);
});
