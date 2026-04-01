package com.eduprime.arduinobt.screens;

import android.bluetooth.BluetoothDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.eduprime.arduinobt.R;

import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {

    private final List<BluetoothDevice> devices;
    private final OnDeviceClickListener listener;

    public interface OnDeviceClickListener {
        void onDeviceClick(BluetoothDevice device);
    }

    public DeviceAdapter(List<BluetoothDevice> devices, OnDeviceClickListener listener) {
        this.devices = devices;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_device, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BluetoothDevice device = devices.get(position);
        String name = device.getName();
        holder.deviceName.setText(name != null ? name : "Unknown Device");
        holder.deviceAddress.setText(device.getAddress());
        holder.deviceStatus.setText("Paired");
        holder.itemView.setOnClickListener(v -> listener.onDeviceClick(device));
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView deviceName, deviceAddress, deviceStatus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            deviceName = itemView.findViewById(R.id.deviceName);
            deviceAddress = itemView.findViewById(R.id.deviceAddress);
            deviceStatus = itemView.findViewById(R.id.deviceStatus);
        }
    }
}
