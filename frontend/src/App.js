import * as React from 'react'
import { Admin, Resource, ListGuesser, EditGuesser } from 'react-admin'
import { jmixAuthProvider } from './jmix-ra/jmixAuthProvider'
import { jmixDataProvider } from './jmix-ra/jmixDataProvider'
import { CategoryList, CategoryEdit, CategoryCreate } from "./category"
import { CurrencyCreate, CurrencyEdit, CurrencyList } from "./currency"
import { AccountCreate, AccountEdit, AccountList } from "./account"


const App = () => {
    return (
        <Admin dataProvider={jmixDataProvider} authProvider={jmixAuthProvider}>
            <Resource name="akk_Account-api" options={{label: "Account"}} list={AccountList} edit={AccountEdit} create={AccountCreate}/>
            <Resource name="akk_Category" options={{label: "Category"}} list={CategoryList} edit={CategoryEdit} create={CategoryCreate}/>
            <Resource name="akk_Currency" options={{label: "Currency"}} list={CurrencyList} edit={CurrencyEdit} create={CurrencyCreate}/>
            <Resource name="akk_User" list={ListGuesser} />
        </Admin>
    )
}

export default App;
