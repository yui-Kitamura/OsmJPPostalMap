// OsmJPPostalMap - Web Viewer Script

document.addEventListener('DOMContentLoaded', function() {
    // --- Constants ---
    const DATA_BASE_URL = 'https://yui-kitamura.github.io/OsmJpPostalMapDataSource/';
    const TILE_SERVER_URL = 'https://tile.openstreetmap.jp/{z}/{x}/{y}.png';
    const JP_POST_RED = '#E60012';
    const MAX_MARKERS = 1000;
    const MIN_ZOOM = 7;
    const JP_BOUNDS = L.latLngBounds([20.20, 122.70], [45.60, 154.00]);
    const TOKYO_POST_OFFICE = [35.6801350, 139.7646546];

    const STATUS = {
        OPEN: { color: '#81C784', label: '営業中/収集可', symbol: '〒' },
        EVENT_SOON: { color: '#FFA500', label: 'まもなく終了/収集', symbol: '〒' },
        CLOSED: { color: '#808080', label: '終了/休業', symbol: '〒' },
        CLOSING_BUT_OPEN_SOON: { color: '#556B2F', label: '営業開始前', symbol: '〒' },
        ERROR: { color: '#FF5252', label: '解析エラー', symbol: '△' },
        UNKNOWN: { color: 'transparent', label: '不明', symbol: '？' }
    };

    // --- State ---
    let map;
    let boundaryData = null;
    let loadedAreas = new Set();
    let poisByArea = new Map(); // areaKey -> Array of POIs
    let markerLayer;
    let isPostOfficeFilterActive = false;
    let isOpeningOnlyFilterActive = false;
    let isErrorFilterActive = false;
    let currentMarkerUpdateId = 0;
    let updateMarkersTimeout = null;

    // --- 1. Initialize Map ---
    map = L.map('map', {
        zoomControl: false,
        attributionControl: false,
        minZoom: MIN_ZOOM,
        maxBounds: JP_BOUNDS,
        maxBoundsViscosity: 1.0
    }).setView(TOKYO_POST_OFFICE, 16);

    L.tileLayer(TILE_SERVER_URL, {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors, <a href="https://tile.openstreetmap.jp/">OpenStreetMap Japan</a>'
    }).addTo(map);

    markerLayer = L.layerGroup().addTo(map);

    // --- 2. Data Loading & Marker Management ---
    async function init() {
        try {
            const response = await fetch(DATA_BASE_URL + 'master/boundary.json');
            boundaryData = await response.json();
            
            // Pre-calculate prefecture bounding boxes for faster lookup
            for (const prefCode in boundaryData) {
                const subs = boundaryData[prefCode].sub;
                let minLat = 90, maxLat = -90, minLon = 180, maxLon = -180;
                for (const subCode in subs) {
                    const b = subs[subCode];
                    minLat = Math.min(minLat, b.minLat);
                    maxLat = Math.max(maxLat, b.maxLat);
                    minLon = Math.min(minLon, b.minLon);
                    maxLon = Math.max(maxLon, b.maxLon);
                }
                boundaryData[prefCode].bb = { minLat, maxLat, minLon, maxLon };
            }

            checkAndLoadVisibleAreas();
            updateClockIcon();
            updateScaleBar();
            fetchLastUpdated();
            setInterval(updateClockIcon, 60000); // Update clock every minute
        } catch (err) {
            console.error('Failed to load boundary data', err);
        }
    }

    async function fetchLastUpdated() {
        try {
            const response = await fetch(DATA_BASE_URL + 'data/date.json');
            const data = await response.json();
            const lastUpdatedEl = document.getElementById('last-updated');
            if (lastUpdatedEl && data.lastUpdated) {
                lastUpdatedEl.textContent = '最終データ更新日時: ' + data.lastUpdated;
            }
        } catch (err) {
            console.error('Failed to fetch last updated date', err);
        }
    }

    const loadingAreas = new Set();
    async function checkAndLoadVisibleAreas(forceUpdate = false) {
        if (!boundaryData) return;

        const bounds = map.getBounds();
        const zoom = map.getZoom();

        const zoomMessage = document.getElementById('zoom-message');
        if (zoom < 12) {
            zoomMessage.classList.remove('hidden');
        } else {
            zoomMessage.classList.add('hidden');
        }

        // Margin for keeping data (20% of view size)
        const latExt = (bounds.getNorth() - bounds.getSouth()) * 0.2;
        const lonExt = (bounds.getEast() - bounds.getWest()) * 0.2;
        const keepBounds = L.latLngBounds(
            [bounds.getSouth() - latExt, bounds.getWest() - lonExt],
            [bounds.getNorth() + latExt, bounds.getEast() + lonExt]
        );

        const activeAreaKeys = new Set();
        const areasToLoad = [];

        if (zoom >= 12) {
            for (const prefCode in boundaryData) {
                const pref = boundaryData[prefCode];
                // Spatial filter: check if prefecture overlaps with current viewport + margin
                if (keepBounds.getSouth() <= pref.bb.maxLat && keepBounds.getNorth() >= pref.bb.minLat &&
                    keepBounds.getWest() <= pref.bb.maxLon && keepBounds.getEast() >= pref.bb.minLon) {
                    
                    const subs = pref.sub;
                    for (const subCode in subs) {
                        const b = subs[subCode];
                        const areaKey = `${prefCode}_${subCode}`;
                        
                        const isVisible = bounds.getSouth() <= b.maxLat && bounds.getNorth() >= b.minLat &&
                                          bounds.getWest() <= b.maxLon && bounds.getEast() >= b.minLon;
                        const shouldKeep = keepBounds.intersects([[b.minLat, b.minLon], [b.maxLat, b.maxLon]]);

                        if (isVisible) {
                            activeAreaKeys.add(areaKey);
                            if (!loadedAreas.has(areaKey) && !loadingAreas.has(areaKey)) {
                                areasToLoad.push({ prefCode, subCode });
                            }
                        } else if (shouldKeep) {
                            if (loadedAreas.has(areaKey) || loadingAreas.has(areaKey)) {
                                activeAreaKeys.add(areaKey);
                            }
                        }
                    }
                }
            }
        }

        let newLoad = false;
        if (areasToLoad.length > 0) {
            // Load areas in parallel
            areasToLoad.forEach(a => loadArea(a.prefCode, a.subCode));
            newLoad = true;
        }

        // Purge out-of-range areas
        let purged = false;
        const toPurge = [];
        for (const loadedKey of loadedAreas) {
            if (!activeAreaKeys.has(loadedKey)) {
                toPurge.push(loadedKey);
            }
        }
        toPurge.forEach(key => {
            loadedAreas.delete(key);
            poisByArea.delete(key);
            purged = true;
        });

        if (newLoad || purged || forceUpdate) requestUpdateMarkers();
    }

    function requestUpdateMarkers() {
        if (updateMarkersTimeout) return;
        updateMarkersTimeout = setTimeout(() => {
            updateMarkers();
            updateMarkersTimeout = null;
        }, 50); // Small buffer to batch multiple area loads
    }

    async function loadArea(prefCode, subCode) {
        const areaKey = `${prefCode}_${subCode}`;
        if (loadingAreas.has(areaKey)) return;
        loadingAreas.add(areaKey);

        let url = (subCode === '00' && Object.keys(boundaryData[prefCode].sub).length === 1)
            ? `${DATA_BASE_URL}data/jPostal_${prefCode}.json`
            : `${DATA_BASE_URL}data/jPostal_${prefCode}_${subCode}.json`;

        try {
            const response = await fetch(url);
            const json = await response.json();
            processPois(areaKey, json.data);
            loadedAreas.add(areaKey);
            requestUpdateMarkers();
        } catch (err) {
            console.warn(`Failed to load area ${areaKey}`, err);
        } finally {
            loadingAreas.delete(areaKey);
        }
    }

    function processPois(areaKey, pois) {
        const filteredPois = pois.filter(poi => 
            poi.tags.amenity === 'post_box' || poi.tags.amenity === 'post_office'
        );
        poisByArea.set(areaKey, filteredPois);
    }

    function getAllPois() {
        const all = [];
        const seen = new Set();
        for (const areaPois of poisByArea.values()) {
            for (const poi of areaPois) {
                const key = `${poi.type}_${poi.id}`;
                if (!seen.has(key)) {
                    seen.add(key);
                    all.push(poi);
                }
            }
        }
        return all;
    }

    function updateMarkers() {
        const updateId = ++currentMarkerUpdateId;
        const center = map.getCenter();
        const bounds = map.getBounds();
        const now = new Date();
        
        // Get POIs only from areas that are visible
        const candidatePois = [];
        const seen = new Set();
        for (const [areaKey, areaPois] of poisByArea.entries()) {
            const [prefCode, subCode] = areaKey.split('_');
            const b = boundaryData[prefCode].sub[subCode];
            // Check if this area overlaps with current view
            if (bounds.getSouth() <= b.maxLat && bounds.getNorth() >= b.minLat &&
                bounds.getWest() <= b.maxLon && bounds.getEast() >= b.minLon) {
                for (const poi of areaPois) {
                    const key = `${poi.type}_${poi.id}`;
                    if (!seen.has(key)) {
                        seen.add(key);
                        candidatePois.push(poi);
                    }
                }
            }
        }
        
        // Filter POIs that match active filters
        let filtered = candidatePois.filter(poi => {
            if (!bounds.contains([poi.lat, poi.lon])) return false;
            
            const status = getPoiStatus(poi, now);
            if (isErrorFilterActive) {
                if (status.label !== STATUS.ERROR.label) return false;
            } else if (isOpeningOnlyFilterActive) {
                if (status.label !== STATUS.OPEN.label && status.label !== STATUS.EVENT_SOON.label) return false;
            }
            
            if (isPostOfficeFilterActive && poi.tags.amenity !== 'post_office') return false;
            
            return true;
        });

        const limitMessage = document.getElementById('limit-message');
        let displayPois = filtered;

        if (filtered.length > MAX_MARKERS) {
            limitMessage.classList.remove('hidden');
            // Sort by distance to center and limit for rendering
            displayPois = filtered.sort((a, b) => {
                const distA = center.distanceTo([a.lat, a.lon]);
                const distB = center.distanceTo([b.lat, b.lon]);
                return distA - distB;
            }).slice(0, MAX_MARKERS);
        } else {
            limitMessage.classList.add('hidden');
        }

        // Render markers
        markerLayer.clearLayers();
        
        // 1st pass: render base icons (no status/rings)
        const baseStatus = { ...STATUS.UNKNOWN, symbol: '〒' };
        const markerData = displayPois.map(poi => {
            const icon = createCustomIcon(poi, baseStatus);
            const marker = L.marker([poi.lat, poi.lon], { icon: icon });
            marker.on('click', () => showPoiDetails(poi));
            marker.addTo(markerLayer);
            return { poi, marker };
        });

        // 2nd pass: update with status (async & chunked)
        let index = 0;
        function updateNextBatch() {
            if (updateId !== currentMarkerUpdateId) return;

            const batchSize = 100;
            const end = Math.min(index + batchSize, markerData.length);
            for (; index < end; index++) {
                const item = markerData[index];
                const status = getPoiStatus(item.poi, now);
                item.marker.setIcon(createCustomIcon(item.poi, status));
                item.marker.setZIndexOffset(getMarkerZIndex(status));
            }

            if (index < markerData.length) {
                requestAnimationFrame(updateNextBatch);
            }
        }
        requestAnimationFrame(updateNextBatch);
    }

    function getStatusPriority(status) {
        if (!status || !status.label) return 0;
        switch (status.label) {
            case STATUS.ERROR.label: return 120;
            case STATUS.EVENT_SOON.label: return 100;
            case STATUS.OPEN.label: return 80;
            case STATUS.CLOSING_BUT_OPEN_SOON.label: return 60;
            case STATUS.CLOSED.label: return 40;
            case STATUS.UNKNOWN.label: return 0;
            default: return 0;
        }
    }

    function getMarkerZIndex(status) {
        let priority = getStatusPriority(status);
        let eventScore = 0;
        if (status.nextEvent) {
            eventScore = 20000 - (status.nextEvent.dayOffset * 1440 + status.nextEvent.time);
        }
        return priority * 100000 + eventScore;
    }

    function createCustomIcon(poi, status) {
        const color = JP_POST_RED;
        const ringColor = status.color;
        const isPostBox = poi.tags.amenity === 'post_box';
        const symbol = status.symbol || '〒';
        
        let ringSvg = '';
        if (ringColor !== 'transparent') {
            if (isPostBox) {
                // Post Box Ring
                if (status.label === STATUS.EVENT_SOON.label && status.nextEvent && status.nextEvent.dayOffset === 0) {
                    const now = new Date();
                    const nowMinutes = now.getHours() * 60 + now.getMinutes();
                    const remaining = status.nextEvent.time - nowMinutes;
                    const ratio = Math.max(0, Math.min(1, remaining / 60));
                    const fullLen = 2 * Math.PI * 14;
                    const dashArray = fullLen * ratio;
                    ringSvg = `<circle cx="16" cy="16" r="14" fill="none" stroke="${ringColor}" stroke-width="3" 
                        stroke-dasharray="${dashArray} ${fullLen}" transform="rotate(-90 16 16)" class="status-ring"/>`;
                } else {
                    let actualRingColor = ringColor;
                    if (status.label === STATUS.CLOSING_BUT_OPEN_SOON.label) {
                        actualRingColor = STATUS.CLOSED.color;
                    }
                    ringSvg = `<circle cx="16" cy="16" r="14" fill="none" stroke="${actualRingColor}" stroke-width="3" class="status-ring"/>`;
                    
                    // Android-like dot for next collection time
                    if ((status.label === STATUS.OPEN.label || status.label === STATUS.CLOSING_BUT_OPEN_SOON.label) && status.nextEvent && status.nextEvent.dayOffset === 0) {
                        const h = Math.floor(status.nextEvent.time / 60);
                        const m = status.nextEvent.time % 60;
                        const angle = (h + m / 60) * 30 - 90;
                        const rad = angle * Math.PI / 180;
                        const dx = 16 + 14 * Math.cos(rad);
                        const dy = 16 + 14 * Math.sin(rad);
                        const dotColor = (status.label === STATUS.CLOSING_BUT_OPEN_SOON.label) ? '#00FF00' : 'white';
                        ringSvg += `<circle cx="${dx}" cy="${dy}" r="2.5" fill="${dotColor}" stroke="${actualRingColor}" stroke-width="1"/>`;
                    }
                }
            } else {
                // Post Office Ring
                if (status.label === STATUS.EVENT_SOON.label && status.nextEvent && status.nextEvent.dayOffset === 0 && status.nextEvent.type === 'CLOSE') {
                    const now = new Date();
                    const nowMinutes = now.getHours() * 60 + now.getMinutes();
                    const remaining = status.nextEvent.time - nowMinutes;
                    const ratio = Math.max(0, Math.min(1, remaining / 60));
                    const fullLen = 28 * 4;
                    const dashArray = fullLen * ratio;
                    ringSvg = `<rect x="2" y="2" width="28" height="28" fill="none" stroke="${ringColor}" stroke-width="3" 
                        stroke-dasharray="${dashArray} ${fullLen}" class="status-ring"/>`;
                } else {
                    ringSvg = `<rect x="2" y="2" width="28" height="28" fill="none" stroke="${ringColor}" stroke-width="3" class="status-ring"/>`;
                    
                    // Android-like dot for next opening time
                    if (status.label === STATUS.CLOSING_BUT_OPEN_SOON.label && status.nextEvent && status.nextEvent.dayOffset === 0) {
                        const h = Math.floor(status.nextEvent.time / 60);
                        const m = status.nextEvent.time % 60;
                        const angle = (h + m / 60) * 30 - 90;
                        const rad = angle * Math.PI / 180;
                        const dx = 16 + 14 * Math.cos(rad);
                        const dy = 16 + 14 * Math.sin(rad);
                        ringSvg += `<circle cx="${dx}" cy="${dy}" r="3" fill="#00FF00" stroke="${ringColor}" stroke-width="1"/>`;
                    }
                }
            }
        }

        let innerSvg;
        if (isPostBox) {
            innerSvg = `<circle cx="16" cy="16" r="10" fill="${color}" stroke="white" stroke-width="1"/>
                <text x="16" y="21" font-size="14" text-anchor="middle" fill="white" font-weight="bold">${symbol}</text>`;
        } else {
            innerSvg = `<rect x="6" y="6" width="20" height="20" rx="2" fill="${color}" stroke="white" stroke-width="1"/>
                <text x="16" y="21" font-size="14" text-anchor="middle" fill="white" font-weight="bold">${symbol}</text>`;
        }
        
        return L.divIcon({
            className: 'custom-div-icon',
            html: `<svg width="32" height="32" viewBox="0 0 32 32">${ringSvg}${innerSvg}</svg>`,
            iconSize: [32, 32],
            iconAnchor: [16, 16]
        });
    }

    // --- 3. Status Calculation Logic ---
    function getPoiStatus(poi, now) {
        if (!now) now = new Date();
        const mainStatus = calculateStatusInternal(poi, now);

        if (poi.tags.amenity === 'post_office') {
            const lsTag = poi.tags['opening_hours:limited_service'] || poi.tags['collection_times:post_office'];
            if (lsTag) {
                const lsPoi = { tags: { opening_hours: lsTag, amenity: 'post_office' } };
                const lsStatus = calculateStatusInternal(lsPoi, now);
                if (lsStatus.label === STATUS.OPEN.label || lsStatus.label === STATUS.EVENT_SOON.label) {
                    return lsStatus;
                }
            }
        }
        return mainStatus;
    }

    function calculateStatusInternal(poi, now) {
        if (!now) now = new Date();
        const tags = poi.tags;
        const nowMinutes = now.getHours() * 60 + now.getMinutes();
        const todayIdx = now.getDay();
        const revDayMap = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'];

        const isPostBox = tags.amenity === 'post_box';
        const ohStr = isPostBox ? tags.collection_times : tags.opening_hours;

        if (!ohStr) return { ...STATUS.UNKNOWN };
        if (ohStr === '24/7') return { ...STATUS.OPEN };

        if (!poi._parsedSchedule) {
            poi._parsedSchedule = parseFullSchedule(poi);
        }
        const schedule = poi._parsedSchedule;
        const weeklyTable = schedule.weeklyTable;

        // Check if we have ANY valid data (parsing check)
        const hasData = Object.values(weeklyTable).some(v => 
            v !== null && (v.closed || (v.processedTimes && v.processedTimes.length > 0))
        );
        if (!hasData) return { ...STATUS.ERROR };

        let nextEvent = null;
        let followingEvent = null;
        let currentState = STATUS.CLOSED;

        // Search for events in the next 7 days
        for (let i = 0; i < 7; i++) {
            const currentDayIdx = (todayIdx + i) % 7;
            const currentDayKey = revDayMap[currentDayIdx];
            const daySchedule = weeklyTable[currentDayKey];

            if (daySchedule && !daySchedule.closed && daySchedule.processedTimes && daySchedule.processedTimes.length > 0) {
                const times = daySchedule.processedTimes;

                for (const t of times) {
                    if (i === 0) {
                        // Today
                        if (t.end) {
                            // Opening Hours
                            if (nowMinutes < t.start) {
                                if (!nextEvent) {
                                    nextEvent = { time: t.start, type: 'OPEN', dayOffset: i };
                                    currentState = (t.start <= nowMinutes + 60) ? STATUS.EVENT_SOON : STATUS.CLOSED;
                                } else if (!followingEvent) {
                                    followingEvent = { time: t.start, type: 'OPEN', dayOffset: i };
                                }
                            } else if (nowMinutes < t.end) {
                                // Currently open
                                currentState = (t.end <= nowMinutes + 60) ? STATUS.EVENT_SOON : STATUS.OPEN;
                                nextEvent = { time: t.end, type: 'CLOSE', dayOffset: i };
                            } else {
                                // Already passed this slot
                            }
                        } else {
                            // Collection Times
                            if (nowMinutes < t.start) {
                                if (!nextEvent) {
                                    nextEvent = { time: t.start, type: 'COLLECTION', dayOffset: i };
                                    currentState = (t.start <= nowMinutes + 60) ? STATUS.EVENT_SOON : STATUS.OPEN;
                                } else if (!followingEvent) {
                                    followingEvent = { time: t.start, type: 'COLLECTION', dayOffset: i };
                                }
                            }
                        }
                    } else {
                        // Future days
                        if (!nextEvent) {
                            nextEvent = { time: t.start, type: t.end ? 'OPEN' : 'COLLECTION', dayOffset: i };
                        } else if (!followingEvent) {
                            followingEvent = { time: t.start, type: t.end ? 'OPEN' : 'COLLECTION', dayOffset: i };
                        }
                    }
                    if (nextEvent && followingEvent) break;
                }
            }
            if (nextEvent && followingEvent) break;
        }

        // Final state adjustment based on Android's SimpleScheduleParser logic
        if (isPostBox) {
            if (nextEvent && nextEvent.dayOffset === 0) {
                if (nextEvent.time <= nowMinutes + 60) {
                    currentState = STATUS.EVENT_SOON;
                } else {
                    currentState = STATUS.CLOSING_BUT_OPEN_SOON;
                }
            } else {
                currentState = STATUS.CLOSED;
            }
        } else {
            if (nextEvent && nextEvent.dayOffset === 0 && nextEvent.type === 'OPEN') {
                currentState = (nextEvent.time <= nowMinutes + 60) ? STATUS.EVENT_SOON : STATUS.CLOSING_BUT_OPEN_SOON;
            } else if (!nextEvent || nextEvent.dayOffset > 0) {
                currentState = STATUS.CLOSED;
            }
        }

        return { ...currentState, nextEvent, followingEvent };
    }

    function parseTimeToMinutes(timeStr) {
        if (!timeStr) return 0;
        const part = timeStr.trim().split(':');
        const h = parseInt(part[0]);
        const m = part.length > 1 ? parseInt(part[1]) : 0;
        if (isNaN(h) || isNaN(m)) return NaN;
        return h * 60 + m;
    }

    // --- 4. UI Events & Animation ---
    function updateClockIcon() {
        const now = new Date();
        const dayLabels = ['日', '月', '火', '水', '木', '金', '土'];
        const dayText = dayLabels[now.getDay()];
        
        const hours = now.getHours() % 12;
        const minutes = now.getMinutes();
        
        const hourDeg = (hours + minutes / 60) * 30;
        const minDeg = minutes * 6;
        
        // Update main clock
        const mainLabel = document.getElementById('clock-day-label');
        if (mainLabel) mainLabel.textContent = dayText;
        const mainHour = document.getElementById('clock-hour-hand');
        const mainMin = document.getElementById('clock-minute-hand');
        if (mainHour) mainHour.setAttribute('transform', `rotate(${hourDeg} 24 24)`);
        if (mainMin) mainMin.setAttribute('transform', `rotate(${minDeg} 24 24)`);
        
        // Update search clock
        const searchLabel = document.getElementById('search-clock-day-label');
        if (searchLabel) searchLabel.textContent = dayText;
        const searchHour = document.getElementById('search-clock-hour-hand');
        const searchMin = document.getElementById('search-clock-minute-hand');
        if (searchHour) searchHour.setAttribute('transform', `rotate(${hourDeg} 24 24)`);
        if (searchMin) searchMin.setAttribute('transform', `rotate(${minDeg} 24 24)`);

        const today = now.getDay();
        const color = (today === 0) ? 'red' : (today === 6 ? 'blue' : 'black');
        if (mainLabel) mainLabel.setAttribute('fill', color);
        if (searchLabel) searchLabel.setAttribute('fill', color);
    }

    function updateScaleBar() {
        const center = map.getCenter();
        const zoom = map.getZoom();
        const metersPerPixel = 156543.03392 * Math.cos(center.lat * Math.PI / 180) / Math.pow(2, zoom);
        const targetWidthPx = 100;
        const meters = targetWidthPx * metersPerPixel;
        
        let roundedMeters, unit;
        if (meters >= 1000) {
            roundedMeters = Math.round(meters / 1000 * 10) / 10;
            unit = 'km';
        } else {
            roundedMeters = Math.round(meters / 10) * 10;
            unit = 'm';
        }
        
        const actualWidthPx = (unit === 'km' ? roundedMeters * 1000 : roundedMeters) / metersPerPixel;
        const scaleBar = document.getElementById('scale-bar');
        if (scaleBar) {
            scaleBar.style.width = actualWidthPx + 'px';
            scaleBar.textContent = roundedMeters + unit;
        }
    }

    function normalizeNumber(s) {
        if (!s) return '';
        return s.replace(/[０-９]/g, function(c) {
            return String.fromCharCode(c.charCodeAt(0) - 0xFEE0);
        });
    }

    function getAddressText(tags) {
        if (!tags) return '';
        
        function get(key) {
            return normalizeNumber(tags[key] || '').trim();
        }

        const parts = [];
        const postcode = get('addr:postcode');
        if (postcode) parts.push('〒' + postcode);
        
        let addr = '';
        if (tags['addr:full']) {
            addr = get('addr:full');
        } else {
            addr += get('addr:province') || get('addr:prefecture') || '';
            addr += get('addr:county') || '';
            addr += get('addr:city') || '';
            addr += get('addr:suburb') || '';
            addr += get('addr:quarter') || '';
            addr += get('addr:neighbourhood') || '';
            
            const block = get('addr:block_number');
            const house = get('addr:housenumber');
            if (block && house) {
                addr += block + '-' + house;
            } else {
                addr += block + house;
            }

            const housename = get('addr:housename');
            if (housename) {
                addr += ' ' + housename;
            }

            const floor = get('addr:floor');
            if (floor) {
                addr += ' ' + floor;
            }

            const room = get('addr:room');
            if (room) {
                addr += ' ' + room;
            }
        }
        
        if (addr) parts.push(addr);
        return parts.join(' ');
    }

    // Details Dialog
    const overlay = document.getElementById('poi-dialog-overlay');
    const closeBtn = document.getElementById('dialog-close-button');

    function showPoiDetails(poi) {
        const tags = poi.tags;
        const isPostBox = tags.amenity === 'post_box';
        document.getElementById('dialog-title').innerText = tags.name || (isPostBox ? '郵便ポスト' : '郵便局');
        const status = getPoiStatus(poi, new Date());
        const statusEl = document.getElementById('dialog-status');
        statusEl.innerText = status.label;
        statusEl.style.color = status.color;

        const nextEventEl = document.getElementById('dialog-next-event');
        if (status.nextEvent) {
            nextEventEl.innerText = formatNextEvent(status.nextEvent, status.followingEvent, isPostBox);
            nextEventEl.classList.remove('hidden');
        } else {
            nextEventEl.classList.add('hidden');
        }
        
        document.getElementById('dialog-address').innerText = getAddressText(tags) || 'データなし';
        
        let rawTags = [];
        if (isPostBox) {
            if (tags.collection_times) rawTags.push(`collection_times=${tags.collection_times}`);
        } else {
            if (tags.opening_hours) rawTags.push(`opening_hours=${tags.opening_hours}`);
            if (tags['opening_hours:limited_service']) rawTags.push(`opening_hours:limited_service=${tags['opening_hours:limited_service']}`);
        }
        document.getElementById('dialog-raw-tag').innerText = 'tags: ' + (rawTags.length > 0 ? rawTags.join(', ') : 'なし');
        document.getElementById('dialog-check-date').innerText = '最終確認日: ' + (tags.check_date || '不明');

        const table = document.getElementById('dialog-weekly-table');
        const schedule = parseFullSchedule(poi);
        renderWeeklyTable(table, schedule, isPostBox);

        const limitedLayout = document.getElementById('dialog-limited-service-layout');
        const lsTime = tags['opening_hours:limited_service'] || tags['collection_times:post_office'];
        if (lsTime) {
            limitedLayout.classList.remove('hidden');
            const lsSchedule = parseFullSchedule({ tags: { opening_hours: lsTime, amenity: 'post_office' } });
            renderWeeklyTable(document.getElementById('dialog-limited-service-weekly-table'), lsSchedule, false);
            document.getElementById('dialog-limited-service-status').innerText = 'あり';
        } else {
            limitedLayout.classList.add('hidden');
        }

        document.getElementById('dialog-open-osm').onclick = () => {
            window.open(`https://www.openstreetmap.org/${poi.type}/${poi.id}`, '_blank');
        };
        overlay.classList.remove('hidden');
    }

    function parseFullSchedule(poi) {
        const tags = poi.tags;
        const ohStr = tags.opening_hours || tags.collection_times;
        const weeklyTable = { 'Mo': null, 'Tu': null, 'We': null, 'Th': null, 'Fr': null, 'Sa': null, 'Su': null, 'PH': null };
        
        if (!ohStr) return { weeklyTable };

        try {
            const dayMap = { 'Mo': 1, 'Tu': 2, 'We': 3, 'Th': 4, 'Fr': 5, 'Sa': 6, 'Su': 0 };
            const revDayMap = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'];
            const parts = ohStr.split(';');

            parts.forEach(part => {
                part = part.trim();
                const match = part.match(/^([A-Za-z,-]+)\s+(.+)$/);
                if (!match) return;

                const daysStr = match[1];
                const timeValue = match[2];
                const isClosed = (timeValue === 'off');
                const times = isClosed ? [] : timeValue.split(',').map(t => t.trim());
                
                // Pre-process times into numeric objects
                const processedTimes = times.map(t => {
                    const [start, end] = t.includes('-') ? t.split('-') : [t, null];
                    return {
                        start: parseTimeToMinutes(start),
                        end: end ? parseTimeToMinutes(end) : null
                    };
                }).filter(t => !isNaN(t.start) && (t.end === null || !isNaN(t.end)))
                .sort((a, b) => a.start - b.start);

                daysStr.split(',').forEach(dPart => {
                    dPart = dPart.trim();
                    if (dPart === 'PH') {
                        weeklyTable['PH'] = { closed: isClosed, times: times, processedTimes: processedTimes };
                    } else if (dPart.includes('-')) {
                        const dRange = dPart.split('-');
                        if (dRange.length === 2) {
                            const start = dayMap[dRange[0].trim()];
                            const end = dayMap[dRange[1].trim()];
                            if (start !== undefined && end !== undefined) {
                                let curr = start;
                                for (let i = 0; i < 7; i++) {
                                    weeklyTable[revDayMap[curr]] = { closed: isClosed, times: times, processedTimes: processedTimes };
                                    if (curr === end) break;
                                    curr = (curr + 1) % 7;
                                }
                            }
                        }
                    } else if (weeklyTable.hasOwnProperty(dPart)) {
                        weeklyTable[dPart] = { closed: isClosed, times: times, processedTimes: processedTimes };
                    }
                });
            });
        } catch (e) {}
        
        return { weeklyTable };
    }

    function formatNextEvent(next, following, isPostBox) {
        const now = new Date();
        
        const getEventInfo = (ev) => {
            const evTime = new Date(now);
            evTime.setHours(Math.floor(ev.time / 60), ev.time % 60, 0, 0);
            evTime.setDate(now.getDate() + ev.dayOffset);
            
            let prefix = "";
            if (ev.dayOffset === 0) prefix = "本日";
            else if (ev.dayOffset === 1) prefix = "明日";
            else prefix = `${evTime.getMonth() + 1}/${evTime.getDate()}`;
            
            const timeStr = `${String(Math.floor(ev.time / 60)).padStart(2, '0')}:${String(ev.time % 60).padStart(2, '0')}`;
            return { prefix, timeStr, evTime };
        };

        const nextInfo = getEventInfo(next);
        const diffMinutes = Math.floor((nextInfo.evTime - now) / 60000);
        const h = Math.floor(diffMinutes / 60);
        const m = diffMinutes % 60;
        const diffStr = (h > 0) ? `${h}時間${m}分後` : `${m}分後`;

        if (isPostBox) {
            let msg = `次回 ${nextInfo.prefix} ${nextInfo.timeStr} (${diffStr})`;
            if (following) {
                const fInfo = getEventInfo(following);
                msg += `\n逃した場合 ${fInfo.prefix} ${fInfo.timeStr}`;
            }
            return msg;
        } else {
            const typeStr = (next.type === 'CLOSE') ? 'まで' : 'から';
            return `${nextInfo.prefix} ${nextInfo.timeStr}${typeStr} (${diffStr})`;
        }
    }

    function renderWeeklyTable(table, schedule, isPostBox) {
        table.innerHTML = '';
        const weeklyTable = schedule.weeklyTable;
        if (!weeklyTable) return;

        const isSame = (s1, s2) => {
            if (!s1 || !s2) return s1 === s2;
            if (s1.closed !== s2.closed) return false;
            if (s1.times.length !== s2.times.length) return false;
            return s1.times.every((t, i) => t === s2.times[i]);
        };

        const mon = weeklyTable['Mo'];
        let weekdaySame = true;
        ['Tu', 'We', 'Th', 'Fr'].forEach(d => {
            if (!isSame(mon, weeklyTable[d])) weekdaySame = false;
        });

        const groups = [];
        if (weekdaySame) {
            groups.push({ label: '平日', days: ['Mo', 'Tu', 'We', 'Th', 'Fr'] });
        } else {
            ['Mo', 'Tu', 'We', 'Th', 'Fr'].forEach(d => {
                const names = { 'Mo': '月曜', 'Tu': '火曜', 'We': '水曜', 'Th': '木曜', 'Fr': '金曜' };
                groups.push({ label: names[d], days: [d] });
            });
        }
        groups.push({ label: '土曜', days: ['Sa'] });
        groups.push({ label: '日祝', days: ['Su', 'PH'] });

        const now = new Date();
        const currentDayIdx = now.getDay();
        const dayLabels = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'];
        const currentLabel = dayLabels[currentDayIdx];

        groups.forEach(group => {
            const tr = document.createElement('tr');
            const isToday = group.days.includes(currentLabel);
            
            const labelCell = document.createElement('td');
            labelCell.textContent = group.label;
            if (isToday) labelCell.style.fontWeight = 'bold';
            tr.appendChild(labelCell);

            const valueCell = document.createElement('td');
            const data = weeklyTable[group.days[0]];
            if (!data) {
                valueCell.textContent = '不明';
            } else if (data.closed) {
                valueCell.textContent = isPostBox ? '収集なし' : '休業';
            } else {
                valueCell.textContent = data.times.join(', ');
            }
            if (isToday) valueCell.style.fontWeight = 'bold';
            tr.appendChild(valueCell);
            
            table.appendChild(tr);
        });
    }

    // --- 5. Search Logic ---
    const searchOverlay = document.getElementById('search-dialog-overlay');
    const searchInput = document.getElementById('search-input');
    const searchResultsList = document.getElementById('search-results-list');
    const searchLoading = document.getElementById('search-loading');
    let isSearchOpenOnly = false;

    document.getElementById('search-button').addEventListener('click', () => {
        updateClockIcon();
        searchOverlay.classList.remove('hidden');
        searchInput.focus();
    });

    document.getElementById('search-dialog-close-button').addEventListener('click', () => {
        searchOverlay.classList.add('hidden');
    });

    document.getElementById('search-filter-open-button').addEventListener('click', function() {
        isSearchOpenOnly = !isSearchOpenOnly;
        const svg = document.getElementById('search-clock-icon-svg');
        if (svg) {
            svg.querySelector('circle').setAttribute('fill', isSearchOpenOnly ? '#81C784' : 'white');
        }
        performSearch();
    });

    searchInput.addEventListener('input', debounce(performSearch, 300));
    document.getElementById('check-place').addEventListener('change', performSearch);
    document.getElementById('check-post-office').addEventListener('change', performSearch);
    document.getElementById('check-address').addEventListener('change', performSearch);

    function debounce(func, wait) {
        let timeout;
        return function(...args) {
            clearTimeout(timeout);
            timeout = setTimeout(() => func.apply(this, args), wait);
        };
    }

    function performSearch() {
        const query = searchInput.value.toLowerCase().trim();
        if (query.length < 2) {
            searchResultsList.innerHTML = '';
            return;
        }

        const checkPlace = document.getElementById('check-place').checked;
        const checkPostOffice = document.getElementById('check-post-office').checked;
        const checkAddress = document.getElementById('check-address').checked;

        searchLoading.classList.remove('hidden');
        
        const now = new Date();
        const allPois = getAllPois();
        // Use allPois for search
        const results = allPois.filter(poi => {
            const tags = poi.tags;
            let match = false;

            if (checkPlace) {
                if ((tags.name || '').toLowerCase().includes(query)) match = true;
            }
            if (!match && checkPostOffice) {
                if (tags.amenity === 'post_office' && (tags.name || '').toLowerCase().includes(query)) match = true;
            }
            if (!match && checkAddress) {
                // Search in all addr:* tags
                for (const key in tags) {
                    if (key.startsWith('addr:') && (tags[key] || '').toLowerCase().includes(query)) {
                        match = true;
                        break;
                    }
                }
            }

            if (match && isSearchOpenOnly) {
                const status = getPoiStatus(poi, now);
                if (status.label !== STATUS.OPEN.label && status.label !== STATUS.EVENT_SOON.label) match = false;
            }

            return match;
        });

        renderSearchResults(results.slice(0, 50)); // Limit search results to 50 for performance
        searchLoading.classList.add('hidden');
    }

    function renderSearchResults(results) {
        searchResultsList.innerHTML = '';
        const center = map.getCenter();

        results.sort((a, b) => {
            const distA = center.distanceTo([a.lat, a.lon]);
            const distB = center.distanceTo([b.lat, b.lon]);
            return distA - distB;
        });

        results.forEach(poi => {
            const li = document.createElement('li');
            const isPostBox = poi.tags.amenity === 'post_box';
            const iconSvg = isPostBox ? 
                `<svg width="32" height="32" viewBox="0 0 32 32"><circle cx="16" cy="16" r="10" fill="${JP_POST_RED}" stroke="white" stroke-width="1"/><text x="16" y="21" font-size="14" text-anchor="middle" fill="white" font-weight="bold">〒</text></svg>` :
                `<svg width="32" height="32" viewBox="0 0 32 32"><rect x="6" y="6" width="20" height="20" rx="2" fill="${JP_POST_RED}" stroke="white" stroke-width="1"/><text x="16" y="21" font-size="14" text-anchor="middle" fill="white" font-weight="bold">〒</text></svg>`;
            
            const name = poi.tags.name || (isPostBox ? '郵便ポスト' : '郵便局');
            const addr = getAddressText(poi.tags);
            const dist = Math.round(center.distanceTo([poi.lat, poi.lon]));

            li.innerHTML = `
                <div class="result-icon">${iconSvg}</div>
                <div class="result-text">
                    <div class="result-title">${name}</div>
                    <div class="result-subtitle">${addr || '住所情報なし'} (${dist}m)</div>
                </div>
            `;
            li.addEventListener('click', () => {
                map.setView([poi.lat, poi.lon], 16);
                showPoiDetails(poi);
                searchOverlay.classList.add('hidden');
            });
            searchResultsList.appendChild(li);
        });
    }

    let moveEndTimer = null;
    // --- 6. Event Listeners ---
    map.on('moveend', () => {
        if (moveEndTimer) clearTimeout(moveEndTimer);
        moveEndTimer = setTimeout(() => {
            checkAndLoadVisibleAreas(true);
            updateScaleBar();
        }, 800); // Reduced from 1.5s for better responsiveness
    });

    closeBtn.addEventListener('click', () => overlay.classList.add('hidden'));
    overlay.addEventListener('click', (e) => { if (e.target === overlay) overlay.classList.add('hidden'); });

    document.getElementById('gps-button').addEventListener('click', () => {
        if (navigator.geolocation) {
            navigator.geolocation.getCurrentPosition(pos => {
                const lat = pos.coords.latitude;
                const lon = pos.coords.longitude;
                if (JP_BOUNDS.contains([lat, lon])) {
                    map.setView([lat, lon], 16);
                } else {
                    map.setView(TOKYO_POST_OFFICE, 16);
                    alert('日本国外のため東京駅前を表示します');
                }
            }, () => alert('位置情報を取得できませんでした'));
        }
    });

    document.getElementById('filter-button').addEventListener('click', function() {
        isOpeningOnlyFilterActive = !isOpeningOnlyFilterActive;
        const svg = document.getElementById('clock-icon-svg');
        if (svg) {
            const circle = svg.querySelector('circle');
            if (circle) circle.setAttribute('fill', isOpeningOnlyFilterActive ? '#81C784' : 'white');
        }
        requestUpdateMarkers();
    });

    document.getElementById('post-office-filter-button').addEventListener('click', function() {
        isPostOfficeFilterActive = !isPostOfficeFilterActive;
        const rect = document.getElementById('post-office-filter-rect');
        if (rect) rect.setAttribute('fill', isPostOfficeFilterActive ? '#81C784' : 'white');
        requestUpdateMarkers();
    });

    document.getElementById('error-filter-button').addEventListener('click', function() {
        isErrorFilterActive = !isErrorFilterActive;
        const circle = document.getElementById('error-filter-circle');
        if (circle) circle.setAttribute('fill', isErrorFilterActive ? '#FF5252' : 'white');
        requestUpdateMarkers();
    });

    const appOverlay = document.getElementById('app-dialog-overlay');
    document.getElementById('app-link-button').addEventListener('click', () => appOverlay.classList.remove('hidden'));
    document.getElementById('app-dialog-close-button').addEventListener('click', () => appOverlay.classList.add('hidden'));
    appOverlay.addEventListener('click', (e) => { if (e.target === appOverlay) appOverlay.classList.add('hidden'); });

    // --- Start ---
    init();
});
