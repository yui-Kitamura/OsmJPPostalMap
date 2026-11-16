(function(root) {
    function isOpenForFilter(poi, status, statusTypes) {
        if (!poi || !poi.tags || !status || !statusTypes) return false;
        if (status.label === statusTypes.OPEN.label || status.label === statusTypes.EVENT_SOON.label) return true;

        return poi.tags.amenity === 'post_box' &&
            status.label === statusTypes.CLOSING_BUT_OPEN_SOON.label &&
            status.nextEvent != null && status.nextEvent.dayOffset === 0;
    }

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = isOpenForFilter;
    }
    if (root) {
        root.isOpenForFilter = isOpenForFilter;
    }
})(typeof globalThis !== 'undefined' ? globalThis : this);
