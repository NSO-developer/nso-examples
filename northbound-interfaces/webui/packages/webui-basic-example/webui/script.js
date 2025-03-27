// Example requests to NSO JSONRPC

// JSON-RPC request counter, each request should have a unique id.
let requestId = 0;

/**
 * Return JSON-RPC JSON representation of method and params
 * @param {string} method - The JSON-RPC method name
 * @param {object} params - The parameters for the JSON-RPC method
 * @returns {string} - The JSON string representation of the request
 */
const createRequest = (method, params) => {
    requestId += 1;
    return JSON.stringify({
        jsonrpc: '2.0',
        id: requestId,
        method,
        params,
    });
};

/**
 * JSON-RPC helper
 * Return request promise
 * @param {string} method - The JSON-RPC method name
 * @param {object} params - The parameters for the JSON-RPC method
 * @returns {Promise<any>} - The JSON-RPC response result
 * @throws {Error} - If the response status is not 200 or if there is an error
 *                   in the JSON-RPC response
 */
const jsonrpc = async (method, params) => {
    const url = `/jsonrpc/${method}`;
    const body = createRequest(method, params);

    const response = await fetch(url, {
        method: 'POST',
        credentials: 'same-origin',
        headers: {
            Accept: 'application/json;charset=utf-8',
            'Content-Type': 'application/json;charset=utf-8',
        },
        body,
    });

    if (response.status !== 200) {
        throw new Error(`Error in ${method}: ${response.statusText}`);
    }

    const jsonResponse = await response.json();

    if (jsonResponse.error) {
        throw new Error(`Error in ${method}: ${jsonResponse.error.message}`);
    }

    return jsonResponse.result;
};

/**
 * Fetch the system version with the get_system_setting method.
 * @returns {Promise<string>} - The system version
 */
const fetchSystemVersion = async () => jsonrpc('get_system_setting', {
    operation: 'version',
});

/**
 * Fetch the current user with the get_system_setting method.
 * @returns {Promise<string>} - The current user
 */
const fetchCurrentUser = async () => jsonrpc('get_system_setting', {
    operation: 'user',
});


/**
 * Execute a function within the context of a transaction.
 * @param {function} fn - The function to execute within the transaction
 * @returns {Promise<any>} - The result of the function
 * @throws {Error} - If there is an error during the transaction
 */
const withTransaction = async (fn) => {
    // Note: In reality, the transaction handle (th) should be handled in a
    // more sophisticated way. For this example, a read transaction is created
    // and deleted.

    // Create read transaction
    const { th } = await jsonrpc('new_trans', {
        db: 'running',
        mode: 'read',
        conf_mode: 'private',
        tag: 'example-read-tag',
    });

    try {
        // Execute the provided function with the transaction handle
        const result = await fn(th);
        return result;
    } finally {
        // Remove transaction
        await jsonrpc('delete_trans', { th });
    }
};

/**
 * Fetch the current packages with the show config api method.
 * @returns {Promise<Array<{name: string, version: string}>>} - The packages
 */
const showPackages = async () => withTransaction(async (th) => {
    // show operational data for packages
    const result = await jsonrpc('show_config', {
        path: '/ncs:packages/package',
        with_oper: true,
        result_as: 'json',
        th,
    });

    const packages = result?.data?.['tailf-ncs:packages']?.package?.map(
        ({ name, 'package-version': version }) => ({ name, version }),
    );

    return packages;
});

/**
 * Fetch the current devices with the query api method.
 * @returns {Promise<Array<{name: string, address: string}>>} - The devices
 */
const queryDevices = async () => withTransaction(async (th) => {
    // query devices
    const result = await jsonrpc('query', {
        path: '/ncs:devices/device',
        selection: ['name', 'address'],
        th,
    });

    const devices = result?.results.map(([name, address]) => ({
        name,
        address
    }));

    return devices;
});

/**
 * Update the UI with the data list element.
 * @param {string} id - The id of the element
 * @param {string} label - The label of the element
 * @param {string} value - The value of the element
 * @returns {void}
 */
const updateUIDataList = (id, label, value) => {
    // if the element already exists, update the value
    const element = document.getElementById(id);
    if (element) {
        element.innerText = value;
        return;
    }

    // Get data list element
    const dataList = document.getElementById('nso-data-list');

    // Create and append the dt and dd elements
    const dt = document.createElement('dt');
    dt.innerText = label;
    dataList.appendChild(dt);
    const dd = document.createElement('dd');
    dd.id = id;
    dd.innerText = value;
    dataList.appendChild(dd);
};

// Initialize the UI when the window has loaded.
window.addEventListener('load', async () => {
    try {
        // Fetch and update the system version when the window has loaded.
        const version = await fetchSystemVersion();
        updateUIDataList(
            'nso-version',
            'Running NSO version',
            `NSO ${version}`,
        );

        // Fetch and update the current user.
        const username = await fetchCurrentUser();
        updateUIDataList(
            'nso-user',
            'Current user',
            username,
        );

        // Fetch and update the current packages.
        const packages = await showPackages();
        const packageString = packages?.map(
            pkg => `${pkg.name} (v${pkg.version})`,
        ).join(', ');
        updateUIDataList(
            'nso-num-packages',
            'Number of packages',
            packages?.length ? packages?.length : 'No packages found',
        );
        updateUIDataList(
            'nso-packages',
            'Package list',
            packageString,
        );

        // Fetch and update the current devices.
        const devices = await queryDevices();
        const deviceString = devices?.map(
            device => `${device.name} (${device.address})`,
        ).join(', ');
        updateUIDataList(
            'nso-num-devices',
            'Number of devices',
            devices?.length ? devices?.length : 'No devices found',
        );
        updateUIDataList(
            'nso-devices',
            'Device list',
            deviceString,
        );
    } catch (error) {
        console.error('Error during initialization:', error);
        updateUIDataList(
            'nso-error',
            '❌ Error message',
            error.message,
        );
    }
});
