const assert = require('node:assert/strict');
const fs = require('node:fs');
const test = require('node:test');
const vm = require('node:vm');

const script = fs.readFileSync(require.resolve('./script.js'), 'utf8');
const start = script.indexOf('    function renderWeeklyTable(');
const end = script.indexOf('    // --- 5. Search Logic ---', start);
assert.ok(start >= 0 && end > start);
const sandbox = { document: null };
const renderWeeklyTable = vm.runInNewContext(`${script.slice(start, end)}\nrenderWeeklyTable`, sandbox);

function render(schedule) {
    const table = {
        rows: [],
        set innerHTML(value) {
            assert.equal(value, '');
            this.rows = [];
        },
        appendChild(row) {
            this.rows.push(row);
        }
    };
    const document = {
        createElement(tagName) {
            return {
                tagName,
                children: [],
                style: {},
                textContent: '',
                appendChild(child) {
                    this.children.push(child);
                }
            };
        }
    };

    sandbox.document = document;
    renderWeeklyTable(table, schedule, false);
    return table.rows.map(row => row.children.map(cell => cell.textContent));
}

test('keeps Sunday and public holiday together when their schedules match', () => {
    const rows = render({ weeklyTable: {
        Mo: { times: ['09:00-17:00'], closed: false },
        Tu: { times: ['09:00-17:00'], closed: false },
        We: { times: ['09:00-17:00'], closed: false },
        Th: { times: ['09:00-17:00'], closed: false },
        Fr: { times: ['09:00-17:00'], closed: false },
        Sa: { times: [], closed: true },
        Su: { times: ['09:00-12:00'], closed: false },
        PH: { times: ['09:00-12:00'], closed: false }
    } });

    assert.deepEqual(rows.slice(-1), [['日祝', '09:00-12:00']]);
});

test('shows separate Sunday and public holiday rows when their schedules differ', () => {
    const rows = render({ weeklyTable: {
        Mo: { times: ['09:00-17:00'], closed: false },
        Tu: { times: ['09:00-17:00'], closed: false },
        We: { times: ['09:00-17:00'], closed: false },
        Th: { times: ['09:00-17:00'], closed: false },
        Fr: { times: ['09:00-17:00'], closed: false },
        Sa: { times: [], closed: true },
        Su: { times: ['09:00-12:00', '13:00-17:00'], closed: false },
        PH: { times: ['09:00-12:00'], closed: false }
    } });

    assert.deepEqual(rows.slice(-2), [
        ['日曜', '09:00-12:00, 13:00-17:00'],
        ['祝日', '09:00-12:00']
    ]);
});

test('preserves the weekday grouping when Sunday and public holiday are split', () => {
    const rows = render({ weeklyTable: {
        Mo: { times: ['09:00-17:00'], closed: false },
        Tu: { times: ['09:00-17:00'], closed: false },
        We: { times: ['09:00-17:00'], closed: false },
        Th: { times: ['09:00-17:00'], closed: false },
        Fr: { times: ['09:00-17:00'], closed: false },
        Sa: { times: [], closed: true },
        Su: { times: ['09:00-12:00'], closed: false },
        PH: { times: ['10:00-12:00'], closed: false }
    } });

    assert.equal(rows[0][0], '平日');
    assert.deepEqual(rows.slice(-2).map(row => row[0]), ['日曜', '祝日']);
});

test('shows the missing schedule as unknown when only one side is defined', () => {
    const rows = render({ weeklyTable: {
        Mo: { times: ['09:00-17:00'], closed: false },
        Tu: { times: ['09:00-17:00'], closed: false },
        We: { times: ['09:00-17:00'], closed: false },
        Th: { times: ['09:00-17:00'], closed: false },
        Fr: { times: ['09:00-17:00'], closed: false },
        Sa: { times: [], closed: true },
        Su: { times: ['09:00-12:00'], closed: false }
    } });

    assert.deepEqual(rows.slice(-2), [
        ['日曜', '09:00-12:00'],
        ['祝日', '不明']
    ]);
});

test('keeps Sunday and public holiday together when both schedules are missing', () => {
    const rows = render({ weeklyTable: {
        Mo: { times: ['09:00-17:00'], closed: false },
        Tu: { times: ['09:00-17:00'], closed: false },
        We: { times: ['09:00-17:00'], closed: false },
        Th: { times: ['09:00-17:00'], closed: false },
        Fr: { times: ['09:00-17:00'], closed: false },
        Sa: { times: [], closed: true }
    } });

    assert.deepEqual(rows.slice(-1), [['日祝', '不明']]);
});
