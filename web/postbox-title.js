(function (root, factory) {
    const formatPostBoxManagementInfo = factory();
    if (typeof module === 'object' && module.exports) {
        module.exports = formatPostBoxManagementInfo;
    }
    root.formatPostBoxManagementInfo = formatPostBoxManagementInfo;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
    return function formatPostBoxManagementInfo(operatorBranch, ref) {
        const branch = operatorBranch == null ? '' : String(operatorBranch).trim();
        const reference = ref == null ? '' : String(ref).trim();
        const values = [branch, reference].filter(Boolean);
        return values.length === 0 ? '' : `（${values.join('：')}）`;
    };
});
