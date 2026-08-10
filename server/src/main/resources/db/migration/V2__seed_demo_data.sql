insert into ip_system(system_code, system_name, base_url, health_status) values
('WMS', 'WMS仓储系统', 'http://wms.internal', 'ONLINE'),
('SAP', 'SAP ERP', 'http://sap-gateway.internal', 'ONLINE'),
('MES', 'MES制造执行系统', 'http://mes.internal', 'ONLINE'),
('OA', 'OA协同办公', 'http://oa.internal', 'DEGRADED'),
('SRM', 'SRM供应商平台', 'http://srm.internal', 'ONLINE'),
('PLATFORM', '接口平台', 'http://localhost:8080', 'ONLINE');

insert into ip_interface(
    interface_code, interface_name, description, source_system_id, target_system_id,
    http_method, interface_path, target_url, enabled, today_calls, success_rate, avg_duration_ms
) values
('WMS_SAP_MATERIAL_QUERY', '物料主数据查询', '演示配置，请在启用前修改目标地址', 1, 2, 'POST', '/open-api/material/query', 'http://sap-gateway.internal/sap/material/query', false, 0, 0, 0),
('MES_WMS_ORDER_PUSH', '生产工单下发', '演示配置，请在启用前修改目标地址', 3, 1, 'POST', '/open-api/work-order/push', 'http://wms.internal/api/work-order/receive', false, 0, 0, 0);
