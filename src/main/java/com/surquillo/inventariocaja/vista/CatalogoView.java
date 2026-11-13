package com.surquillo.inventariocaja.vista;

import com.surquillo.inventariocaja.modelo.stock.*;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.Map;

/** Catálogo por descripción y categoría, con listas incrementales y edición manual. */
public class CatalogoView {
    private final SesionBeta sesion;
    private record FilaCatalogo(Variante articulo,String descripcion) {}
    private final TreeTableView<FilaCatalogo> tabla=new TreeTableView<>();
    private TitledPane editor;
    private String siguienteCursor="";
    private final TextField buscar=Ui.campo("Buscar descripción, código o característica");
    private final TextField sku=Ui.campo("SKU único"),descripcion=Ui.campo("Descripción"),tipo=Ui.campo("Modelo o tipo"),
        contenido=Ui.campo("Contenido opcional"),unidadContenido=Ui.campo("kg, m²…"),precio=Ui.campo("Precio sugerido opcional"),
        costo=Ui.campo("Vacío si se desconoce");
    private final ComboBox<String> categoria=opcion("Categoría"),marca=opcion("Marca opcional"),
        presentacion=opcion("Bolsa, caja…"),unidad=opcion("Unidad de stock");
    private final ComboBox<String> categoriaFiltro=opcion("Todas las categorías"),marcaFiltro=opcion("Todas las marcas"),
        unidadFiltro=opcion("Todas las unidades"),presentacionFiltro=opcion("Todas las presentaciones");
    private final TextField tipoFiltro=Ui.campo("Cualquier modelo");
    private final CheckBox fraccion=new CheckBox("Permite cantidades fraccionadas"),activo=new CheckBox("Artículo activo");
    private final ComboBox<Variante.Origen> origen=new ComboBox<>(),origenFiltro=new ComboBox<>();
    private final ComboBox<FiltroCatalogo.Estado> estadoFiltro=new ComboBox<>();
    private final AtributosForm atributos=new AtributosForm(),atributosFiltro=new AtributosForm();
    private Variante seleccionado;
    private String cursor="";
    private FiltroCatalogo filtroAplicado;
    private final Label resumen=new Label();
    public CatalogoView(SesionBeta sesion) { this.sesion=sesion; }
    private static ComboBox<String> opcion(String pista) {
        var combo=new ComboBox<String>();combo.setEditable(true);combo.setPromptText(pista);combo.setPrefWidth(180);return combo;
    }
    private String texto(ComboBox<String> combo) { return combo.getEditor().getText(); }
    private void valor(ComboBox<String> combo,String texto) { combo.setValue(texto);combo.getEditor().setText(texto); }
    public Parent crear() {
        buscar.setOnAction(e->Ui.accion(this::buscarDesdeInicio));
        var columna=new TreeTableColumn<FilaCatalogo,String>("Descripción");
        columna.setCellValueFactory(c->new javafx.beans.property.ReadOnlyStringWrapper(c.getValue().getValue().descripcion()));
        columna.setPrefWidth(800);columna.setSortable(false);tabla.getColumns().add(columna);
        tabla.setShowRoot(false);tabla.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);tabla.setPrefHeight(310);
        tabla.setRowFactory(v->new TreeTableRow<>() {
            @Override protected void updateItem(FilaCatalogo fila,boolean vacia) {
                super.updateItem(fila,vacia);getStyleClass().remove("category-heading");
                if(!vacia && fila!=null && fila.articulo()==null)getStyleClass().add("category-heading");
            }
        });
        tabla.getSelectionModel().selectedItemProperty().addListener((o,a,v)->{if(v!=null && v.getValue().articulo()!=null)cargar(v.getValue().articulo());});
        origen.getItems().setAll(Variante.Origen.values());origen.setValue(Variante.Origen.COMPRADO);
        origenFiltro.getItems().setAll(Variante.Origen.values());origenFiltro.setPromptText("Cualquier origen");
        estadoFiltro.getItems().setAll(FiltroCatalogo.Estado.values());estadoFiltro.setValue(FiltroCatalogo.Estado.ACTIVOS);
        activo.setSelected(true);valor(unidad,"unidad");
        var filtros=new GridPane();filtros.setHgap(10);filtros.setVgap(10);
        filtros.addRow(0,new Label("Marca"),marcaFiltro,new Label("Unidad"),unidadFiltro);
        filtros.addRow(1,new Label("Presentación"),presentacionFiltro,new Label("Modelo"),tipoFiltro);
        filtros.addRow(2,new Label("Origen"),origenFiltro,new Label("Estado"),estadoFiltro);
        var avanzados=new TitledPane("Más filtros",Ui.panel(filtros,new TitledPane("Filtrar características",atributosFiltro.crear())));
        avanzados.setExpanded(false);
        var form=new GridPane();form.setHgap(12);form.setVgap(10);
        form.addRow(0,new Label("SKU"),sku,new Label("Descripción"),descripcion);
        form.addRow(1,new Label("Categoría"),categoria,new Label("Marca"),marca);
        form.addRow(2,new Label("Presentación"),presentacion,new Label("Tipo / modelo"),tipo);
        form.addRow(3,new Label("Unidad de stock"),unidad,new Label("Origen"),origen);
        form.addRow(4,new Label("Contenido de presentación"),contenido,new Label("Unidad del contenido"),unidadContenido);
        form.addRow(5,new Label("Precio sugerido"),precio,new Label("Costo opcional"),costo);
        form.addRow(6,fraccion,activo);
        var caracteristicas=new TitledPane("Características del artículo",atributos.crear());caracteristicas.setExpanded(false);
        editor=new TitledPane("Crear o editar artículo",Ui.panel(
            new Label("La unidad y precisión se conservan al editar. Puedes escribir las opciones manualmente."),
            form,caracteristicas,new HBox(10,Ui.boton("Nuevo artículo",this::limpiar),Ui.boton("Guardar artículo",this::guardar))));
        editor.setExpanded(false);
        var articulos=Ui.desplazar(Ui.panel(new HBox(10,new Label("Categoría"),categoriaFiltro,buscar,
            Ui.boton("Buscar",this::buscarDesdeInicio),Ui.boton("Limpiar filtros",this::limpiarFiltros),
            Ui.boton("Añadir artículo",()->{limpiar();editor.setExpanded(true);sku.requestFocus();})),
            avanzados,tabla,new HBox(10,resumen,Ui.boton("Siguiente página",()->{
                if(!siguienteCursor.isEmpty()){cursor=siguienteCursor;refrescar();}
            })),editor));
        var pestanas=new TabPane(new Tab("Artículos",articulos),
            new Tab("Categorías y listas",new ListasCatalogoView(sesion.catalogo,this::cargarOpciones).crear()));
        pestanas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Ui.accion(()->{cargarOpciones();refrescar();});return pestanas;
    }
    private FiltroCatalogo filtro() {
        return new FiltroCatalogo(buscar.getText(),texto(categoriaFiltro),texto(marcaFiltro),texto(unidadFiltro),
            texto(presentacionFiltro),tipoFiltro.getText(),origenFiltro.getValue(),estadoFiltro.getValue(),atributosFiltro.valores());
    }
    public void refrescar() {
        var nuevo=filtro();if(!nuevo.equals(filtroAplicado)){cursor="";filtroAplicado=nuevo;}
        var pagina=sesion.catalogo.buscar(nuevo,cursor,100);
        siguienteCursor=pagina.isEmpty()?"":pagina.getLast().sku();
        var grupos=new java.util.TreeMap<String,java.util.List<Variante>>(String.CASE_INSENSITIVE_ORDER);
        for(var v:pagina)grupos.computeIfAbsent(v.categoria().isBlank()?"Sin categoría":v.categoria(),k->new java.util.ArrayList<>()).add(v);
        var raiz=new TreeItem<>(new FilaCatalogo(null,"Catálogo"));
        grupos.forEach((nombre,articulos)->{
            var grupo=new TreeItem<>(new FilaCatalogo(null,nombre));grupo.setExpanded(true);
            articulos.stream().sorted(java.util.Comparator.comparing(Variante::descripcion,String.CASE_INSENSITIVE_ORDER).thenComparing(Variante::sku))
                .forEach(v->grupo.getChildren().add(new TreeItem<>(new FilaCatalogo(v,v.descripcion()))));
            raiz.getChildren().add(grupo);
        });
        tabla.setRoot(raiz);resumen.setText(pagina.size()+" artículos en esta página");
    }
    private void buscarDesdeInicio() { cursor="";refrescar(); }
    private void limpiarFiltros() {
        buscar.clear();tipoFiltro.clear();for(var c:java.util.List.of(categoriaFiltro,marcaFiltro,unidadFiltro,presentacionFiltro)){c.setValue(null);c.getEditor().clear();}
        origenFiltro.setValue(null);estadoFiltro.setValue(FiltroCatalogo.Estado.ACTIVOS);atributosFiltro.cargar(Map.of());buscarDesdeInicio();
    }
    private void cargarOpciones() {
        opciones(categoria,categoriaFiltro,ListaCatalogo.CATEGORIA);opciones(marca,marcaFiltro,ListaCatalogo.MARCA);
        opciones(unidad,unidadFiltro,ListaCatalogo.UNIDAD);opciones(presentacion,presentacionFiltro,ListaCatalogo.PRESENTACION);
    }
    private void opciones(ComboBox<String> editor,ComboBox<String> filtro,ListaCatalogo lista) {
        String actual=texto(editor),actualFiltro=texto(filtro);
        editor.getItems().setAll(sesion.catalogo.opciones(lista,100));
        filtro.getItems().setAll(sesion.catalogo.listarOpciones(lista,"","",100,true).stream().map(OpcionCatalogo::nombre).toList());
        valor(editor,actual);valor(filtro,actualFiltro);
    }
    private void guardar() {
        var v=new Variante(sku.getText(),descripcion.getText(),texto(categoria),texto(marca),texto(presentacion),tipo.getText(),
            texto(unidad),fraccion.isSelected(),origen.getValue(),Ui.opcional(contenido.getText()),unidadContenido.getText(),
            Ui.opcional(precio.getText()),Ui.opcional(costo.getText()),activo.isSelected(),seleccionado==null?0:seleccionado.version(),atributos.valores());
        if(seleccionado==null)sesion.catalogo.crear(v);else sesion.catalogo.actualizar(v);
        limpiar();cargarOpciones();buscarDesdeInicio();Ui.informar("Artículo guardado.");
    }
    private void cargar(Variante v) {
        editor.setExpanded(true);
        seleccionado=v;sku.setText(v.sku());sku.setDisable(true);descripcion.setText(v.descripcion());
        valor(categoria,v.categoria());valor(marca,v.marca());valor(presentacion,v.presentacion());tipo.setText(v.tipo());
        valor(unidad,v.unidad());unidad.setDisable(true);fraccion.setSelected(v.permiteFraccion());fraccion.setDisable(true);
        origen.setValue(v.origen());contenido.setText(v.contenido()==null?"":v.contenido().toPlainString());unidadContenido.setText(v.unidadContenido());
        precio.setText(v.precioVenta()==null?"":v.precioVenta().toPlainString());costo.setText(v.costo()==null?"":v.costo().toPlainString());
        activo.setSelected(v.activo());atributos.cargar(v.atributos());
    }
    private void limpiar() {
        seleccionado=null;tabla.getSelectionModel().clearSelection();
        for(var t:new TextField[]{sku,descripcion,tipo,contenido,unidadContenido,precio,costo})t.clear();
        valor(categoria,"");valor(marca,"");valor(presentacion,"");valor(unidad,"unidad");atributos.cargar(Map.of());
        sku.setDisable(false);unidad.setDisable(false);fraccion.setDisable(false);fraccion.setSelected(false);activo.setSelected(true);origen.setValue(Variante.Origen.COMPRADO);
    }
}
