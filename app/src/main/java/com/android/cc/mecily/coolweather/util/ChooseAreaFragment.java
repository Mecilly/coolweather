package com.android.cc.mecily.coolweather.util;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.cc.mecily.coolweather.R;
import com.android.cc.mecily.coolweather.db.City;
import com.android.cc.mecily.coolweather.db.County;
import com.android.cc.mecily.coolweather.db.Province;

import org.litepal.crud.DataSupport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * Created by Mecily on 2017/10/13.
 */

public class ChooseAreaFragment extends Fragment {
   public static final int LEVEL_PROVINCE=0;
    public static final int LEVEL_CITY=1;
    public static final int LEVEL_COUNTY=2;
    private ProgressDialog progressDialog;
    private TextView titleText;
    private Button backButton;
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private List<String> dataList=new ArrayList<>();

    private List<Province> provinceList;
    private List<City> cityList;
    private List<County> countyList;
    private Province selectedProvince;
    private City selectedCity;
    private int currentLevel;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState){
        View view=inflater.inflate(R.layout.choose_area,container,false);
        //可以看到，这个也算是固定格式一种了,三个参数，第一个是对应的布局文件，第二个是ViewGroup，是
        //是上面传入的，第三个管他呢，写成false就对了
        titleText=(TextView)view.findViewById(R.id.title_text);
        backButton=(Button)view.findViewById(R.id.back_button);
        listView=(ListView)view.findViewById(R.id.list_view);
        adapter=new ArrayAdapter<>(getContext(),
                android.R.layout.simple_list_item_1,dataList);
        listView.setAdapter(adapter);
        return view;

    }
    @Override
    public void onActivityCreated(Bundle savedInstanceState){
        super.onActivityCreated(savedInstanceState);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener(){
            @Override
            public void onItemClick(AdapterView<?>parent,View view,int position,
                                    long id){
                if(currentLevel==LEVEL_PROVINCE){
                    selectedProvince=provinceList.get(position);
                    queryCities();

                }else if(currentLevel==LEVEL_CITY){
                    selectedCity=cityList.get(position);
                    queryCounties();
                }
            }
        });
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(currentLevel==LEVEL_COUNTY){
                    queryCities();

                }else if(currentLevel==LEVEL_CITY){
                    queryProvinces();
                }
            }
        });
        queryProvinces();
    }

    //queryProvinces方法是用来加载省级数据的，这个方法的思想是首先优先搜索LitePal
    //如果没有的话再去搜索服务器上的数据，搜索服务器的方法是queryFromServer
    private void queryProvinces(){
        titleText.setText("中国");
        backButton.setVisibility(View.GONE);
        //表示这个控件不可见了，同时位置也不保留
        provinceList= DataSupport.findAll(Province.class);
        //这个方法很特别，这个是LitePal给我们提供的方法，格式如上，得到的是相对应的类型的数据
        if(provinceList.size()>0){
            dataList.clear();
            for(Province province:provinceList){
                dataList.add(province.getProvinceName());

            }
            adapter.notifyDataSetChanged();
            //说穿了就是一个下拉刷新的方法
            listView.setSelection(0);
            currentLevel=LEVEL_PROVINCE;

        }else{
            String address="http://guolin.tech/api/china";
            queryFromServer(address,"province");
            //发现没有数据，所以只能退而求其次去到服务器上查找数据了
        }
    }
    //和上面的queryProvinces一样的方法
    private void queryCities() {
        titleText.setText(selectedProvince.getProvinceName());
        backButton.setVisibility(View.VISIBLE);
        cityList = DataSupport.where("provinceid=?",
                String.valueOf(selectedProvince.getId())).find(City.class);
        if (cityList.size() > 0) {
            dataList.clear();
            for (City city : cityList) {
                dataList.add(city.getCityName());

            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_CITY;
        } else {
            int provinceCode = selectedProvince.getProvinceCode();
            String address = "http://guolin.tech/api/china" + provinceCode;
            queryFromServer(address, "city");

        }
    }

        private void queryCounties(){
        titleText.setText(selectedCity.getCityName());
        backButton.setVisibility(View.VISIBLE);
        countyList=DataSupport.where("cityid=?",
                String.valueOf(selectedCity.getId())).find(County.class);
        if(countyList.size()>0){
            dataList.clear();
            for(County county:countyList){
                dataList.add(county.getCountyName());

            }
            adapter.notifyDataSetInvalidated();
            listView.setSelection(0);
            currentLevel=LEVEL_COUNTY;

        }else{
            int provinceCode=selectedProvince.getProvinceCode();
            int cityCode=selectedCity.getCityCode();
            String address="http://guolin.tech/api/china/"+provinceCode+"/"+
                    cityCode;
            queryFromServer(address,"county");

        }
    }
    //值得注意的是，当我们调用这个queryFromServer的时候，首先会将读取到的数据存储到数据库中，然后
    //再次调用query函数进行访问。
    private void queryFromServer(String address,final String type){
        showProgressDialog();
        HttpUtil.sendOkHttpRequest(address, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        closeProgressDialog();
                        Toast.makeText(getContext(),"加载失败",Toast.LENGTH_SHORT).show();

                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseText=response.body().string();
                boolean result=false;
                if("province".equals(type)){
                    result=Utility.handleProvinceResponse(responseText);

                }else if("city".equals(type)){
                    result=Utility.handleCityResponse(responseText,
                            selectedProvince.getId());

                }else if("county".equals(type)){
                    result=Utility.handleCountyResponse(responseText,
                            selectedCity.getId());
                }
                if(result){
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            closeProgressDialog();
                            if("province".equals(type)){
                                queryProvinces();
                            }else if("city".equals(type)){
                                queryCities();
                            }else if("county".equals(type)){
                                queryCounties();
                            }
                        }
                    });
                }
            }
        });
    }
    private void showProgressDialog(){
        if(progressDialog==null){
            progressDialog=new ProgressDialog(getActivity());
            progressDialog.setMessage("正在加载...");
            progressDialog.setCanceledOnTouchOutside(false);

        }
        progressDialog.show();
    }
    private void closeProgressDialog(){
        if(progressDialog!=null){
            progressDialog.dismiss();
        }
    }
}
