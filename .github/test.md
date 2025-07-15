````mermaid
graph 

  subgraph A-Example-area

    subgraph A-Example-context
            A-Example
            A-Example_login_history

            A-Example --> |<Contain>: 1:N - 기록|A-Example_login_history
    end
    A-Example -->|1:N| A-Example_group_relation
    A-Example --> |1:N| A-Example_contract
    A-Example -->|1:N| A-Example_A-Example_group_relation


    subgraph B-Example-context
        B-Example
    end

    subgraph C-Example-context
        C-Example_group
        C-Example_member
        C-Example_group -->|<Contain>: 1:N - C-Example_group_graph| C-Example_group  
        C-Example_member -->|<Contain>: has-a| C-Example_group
    end


    C-Example_member --> |1:N| A-Example_contract
    C-Example_member -->|<Contain>: 1:N| D-Example_use_permission
    C-Example_member -->|<Contain>: N:1| B-Example
    C-Example_group -->|<Contain>: 1:N| D-Example_sale
    C-Example_member -->|<Contain>: 1:N| D-Example_sale
    C-Example_group -->|1:N| A-Example_group_relation


    subgraph D-Example-context
        D-Example
        D-Example_price
        D-Example_sale
        D-Example_use_permission

        D-Example --> |<Contain>: 1:N| D-Example_use_permission
        D-Example --> |<Contain>: 1:N| D-Example_price
        D-Example_sale --> |<Contain>: 1:1| D-Example_price
    end
    D-Example -->|1:N| A-Example_preset_D-Example_line


    subgraph outer-file-context
        file
    end
    D-Example -->|<Contain>: 1:N - D-Example_image_relation| file


    subgraph E-Example-context
        E-Example
        E-Example_item 
        

        E-Example -->|<Contain>: 1:N| E-Example_item
    end
    C-Example_member -->|<Contain>: 1:N| E-Example
    E-Example_item -->|<Contain>: 1:1| D-Example



  end

````