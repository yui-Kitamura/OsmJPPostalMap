const assert = require('node:assert/strict');
const fs = require('node:fs');
const test = require('node:test');
const vm = require('node:vm');

const isOpenForFilter = require('./open-filter.js');

const statuses = {
    OPEN: { label: 'open' },
    EVENT_SOON: { label: 'event soon' },
    CLOSING_BUT_OPEN_SOON: { label: 'opening soon' }
};

function poi(amenity) {
    return { tags: { amenity } };
}

test('includes ordinary open and event-soon post offices and post boxes', () => {
    for (const amenity of ['post_office', 'post_box']) {
        assert.equal(isOpenForFilter(poi(amenity), statuses.OPEN, statuses), true);
        assert.equal(isOpenForFilter(poi(amenity), statuses.EVENT_SOON, statuses), true);
    }
});

test('includes a post box whose next collection is today and more than an hour away', () => {
    const status = {
        ...statuses.CLOSING_BUT_OPEN_SOON,
        nextEvent: { type: 'COLLECTION', dayOffset: 0, time: 11 * 60 + 1 }
    };

    assert.equal(isOpenForFilter(poi('post_box'), status, statuses), true);
});

test('does not include a post office before opening or a post box collecting on a later day', () => {
    const tomorrowCollection = {
        ...statuses.CLOSING_BUT_OPEN_SOON,
        nextEvent: { type: 'COLLECTION', dayOffset: 1, time: 11 * 60 + 1 }
    };

    assert.equal(isOpenForFilter(poi('post_office'), statuses.CLOSING_BUT_OPEN_SOON, statuses), false);
    assert.equal(isOpenForFilter(poi('post_box'), tomorrowCollection, statuses), false);
});

test('does not include closed, unknown, error, or incomplete statuses', () => {
    assert.equal(isOpenForFilter(poi('post_box'), { label: 'closed' }, statuses), false);
    assert.equal(isOpenForFilter(poi('post_box'), { label: 'opening soon' }, statuses), false);
    assert.equal(isOpenForFilter(poi('post_box'), null, statuses), false);
});

const script = fs.readFileSync(require.resolve('./script.js'), 'utf8');
function section(start, end) {
    const startIndex = script.indexOf(start);
    const endIndex = script.indexOf(end, startIndex);
    assert.ok(startIndex >= 0 && endIndex > startIndex);
    return script.slice(startIndex, endIndex);
}
const { getPoiStatus, STATUS } = vm.runInNewContext([
    section('    const STATUS =', '    // --- State ---'),
    section('    function getPoiStatus(', '    // --- 4. UI Events & Animation ---'),
    section('    const DAY_LABELS =', '    function formatNextEvent('),
    '({ getPoiStatus, STATUS })'
].join('\n'));

test('includes actual parsed collections 59, 60, and 61 minutes away', () => {
    const now = new Date(2026, 6, 21, 10, 0);
    for (const [time, expectedStatus] of [
        ['10:59', STATUS.EVENT_SOON],
        ['11:00', STATUS.EVENT_SOON],
        ['11:01', STATUS.CLOSING_BUT_OPEN_SOON]
    ]) {
        const postBox = { tags: { amenity: 'post_box', collection_times: `Tu ${time}` } };
        const status = getPoiStatus(postBox, now);
        assert.equal(status.label, expectedStatus.label);
        assert.equal(status.nextEvent.dayOffset, 0);
        assert.equal(isOpenForFilter(postBox, status, STATUS), true);
    }
});

test('excludes actual parsed finished, off, missing, invalid, and tomorrow-only collections', () => {
    const now = new Date(2026, 6, 21, 10, 0);
    for (const collectionTimes of ['Tu 09:00; We 11:01', 'Tu off; We 11:01', '', 'invalid', 'We 11:01']) {
        const postBox = { tags: { amenity: 'post_box', collection_times: collectionTimes } };
        assert.equal(isOpenForFilter(postBox, getPoiStatus(postBox, now), STATUS), false, collectionTimes);
    }
});

test('preserves actual parsed post office and limited-service opening filters', () => {
    const now = new Date(2026, 6, 21, 10, 0);
    for (const [openingHours, limitedService, expected] of [
        ['Tu 11:01-17:00', undefined, false],
        ['Tu 09:00-17:00', undefined, true],
        ['Tu 09:00-10:30', undefined, true],
        ['Tu 11:01-17:00', 'Tu 09:00-17:00', true]
    ]) {
        const postOffice = { tags: {
            amenity: 'post_office', opening_hours: openingHours,
            'opening_hours:limited_service': limitedService
        } };
        assert.equal(isOpenForFilter(postOffice, getPoiStatus(postOffice, now), STATUS), expected);
    }
});
