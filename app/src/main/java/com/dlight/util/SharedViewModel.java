package com.dlight.util;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class SharedViewModel extends ViewModel {
    private MutableLiveData<String> selectedItem = new MutableLiveData<>();

    public void setSelectedItem(String item) {
        selectedItem.setValue(item);
    }

    public LiveData<String> getSelectedItem() {
        return selectedItem;
    }
}
